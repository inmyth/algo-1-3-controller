import algotrader.api.Messages.{Load, Start}
import algotrader.api.NativeController
import cats.{Applicative, Monad}
import cats.data.EitherT
import com.hsoft.datamaster.product.{Derivative, ProductTypes}
import com.hsoft.hmm.api.automaton.spi.DefaultAutomaton
import com.hsoft.hmm.api.source.automatonstatus.AutomatonStatus
import com.hsoft.hmm.api.source.position.RiskPositionDetailsSourceBuilder
import com.hsoft.hmm.api.source.pricing.{Pricing, PricingSourceBuilder}
import com.hsoft.hmm.posman.api.position.container.RiskPositionDetailsContainer
import com.ingalys.imc.BuySell
import com.ingalys.imc.depth.DepthOrder
import com.ingalys.imc.order.Order
import com.ingalys.imc.summary.Summary
import guardian.Algo
import guardian.Error
import guardian.Entities.PutCall.{CALL, PUT}
import guardian.Entities.{Direction, PutCall}
import horizontrader.plugins.hmm.connections.service.IDictionaryProvider
import horizontrader.services.instruments.{InstrumentDescriptor, InstrumentInfoService}

import java.util.UUID
import scala.Right
import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.language.higherKinds
import scala.math.BigDecimal.RoundingMode


trait Controller extends NativeController {
  val portfolioId: String
  val ulId: String // "PTT@XBKK"
  val ulInstrument: InstrumentDescriptor
  val hedgeInstrument: InstrumentDescriptor //PTT@XBKK ?? Nop will find a way // String => SET-EMAPI-HMM-PROXY|ADVANC@XBKK
  val dictionaryService: IDictionaryProvider = getService[IDictionaryProvider]
  val dailyResidual = 100

  import algotrader.api.source.summary._

  def getDwList(ds: IDictionaryProvider, ulId: String, exchangeName: String = "SET"): List[InstrumentDescriptor] =
    ds.getDictionary
      .getProducts(null)
      .values()
      .asScala
      .map(p => p.asInstanceOf[Derivative])
      .filter(d => d.getUlId == ulId && d.getProductType == ProductTypes.WARRANT)
      .map(p => getService[InstrumentInfoService]
      .getInstrumentByUniqueId(exchangeName, p.getId))
      .toList

  def getProjectedPrice(inDe: InstrumentDescriptor): Option[Double] = source[Summary].get(inDe).latest.flatMap(_.theoOpenPrice)

  def getProjectedVolume(inDe: InstrumentDescriptor): Option[Long] = source[Summary].get(inDe).latest.flatMap(_.theoOpenVolume)

  def getOwnBestBidPrice(inDe: InstrumentDescriptor): Option[Double] = source[Summary].get(inDe).latest.flatMap(_.buyPrice)

  def getOwnBestAskPrice(inDe: InstrumentDescriptor): Option[Double] = source[Summary].get(inDe).latest.flatMap(_.sellPrice)

  def calcUlQtyPreResidual(ownBestBid: Double, ownBestAsk: Double, marketProjectedPrice: Double, signedDelta: Double, dwId: String, kind: String = "DEFAULT"): Long = {
    val autoSource =  source[AutomatonStatus].get("SET-EMAPI-HMM-PROXY", kind, dwId,"REFERENCE")
    val bdProjectedPrice = BigDecimal(marketProjectedPrice).setScale(2, RoundingMode.HALF_EVEN)
    val bdOwnBestBid = BigDecimal(ownBestBid).setScale(2, RoundingMode.HALF_EVEN)
    val bdOwnBestAsk = BigDecimal(ownBestAsk).setScale(2, RoundingMode.HALF_EVEN)
    /*
    //    DW Projected price <= DW OwnOrder best bid : CALL DW Matched at Bid Sell UL = negative
    //    DW Projected price >= DW OwnOrder best ask : CALL DW Matched at Ask Buy UL = positive
    //    DW Projected price <= DW OwnOrder best bid : PUT DW Matched at Bid Buy UL = positive
    //    DW Projected price >= DW OwnOrder best ask : PUT DW Matched at Ask Sell UL = negative"
     */
    val qty: Long = if(bdProjectedPrice <= bdOwnBestBid){
      kind match {
        case "DEFAULT" =>
          autoSource.latest
            .map(_.buyStatuses
              .filter(p => {
                val v = BigDecimal(p.priceOnMarket).setScale(2, RoundingMode.HALF_EVEN)
                bdProjectedPrice <= v && v <= bdOwnBestBid
              })
              .map(_.qtyOnMarketL)
              .sum
            )
            .getOrElse(0L)
        case "DYNAMIC" =>
          autoSource.latest
            .map(_.buyStatuses(0)).map(p => {
              val v = BigDecimal(p.priceOnMarket).setScale(2, RoundingMode.HALF_EVEN)
              if(bdProjectedPrice <= v && v <= bdOwnBestBid) p.qtyOnMarketL else 0L
            })
            .getOrElse(0L)
        case _ => 0L
      }
    }
    else if (bdProjectedPrice >= bdOwnBestAsk){
      kind match {
        case "DEFAULT" =>
          autoSource.latest
            .map(_.sellStatuses
              .filter(p => {
                val v = BigDecimal(p.priceOnMarket).setScale(2, RoundingMode.HALF_EVEN)
                bdProjectedPrice >= v && v >= bdOwnBestAsk
              })
              .map(_.qtyOnMarketL)
              .sum
            )
            .getOrElse(0L) * -1
        case "DYNAMIC" =>
          autoSource.latest
            .map(_.sellStatuses(0)).map(p => {
              val v = BigDecimal(p.priceOnMarket).setScale(2, RoundingMode.HALF_EVEN)
              if(bdProjectedPrice >= v && v >= bdOwnBestAsk) p.qtyOnMarketL else 0L
            })
            .getOrElse(0L) * -1
        case _ => 0L
      }
    }
    else{
     0L // own orders are not matched
    }
    // CALL dw buy, order is positive , delta is positive, buy dw-> sell ul
    // PUT dw, buy, order is positive, delta is negative, buy dw -> buy ul
    // CALL dw sell, order is negative, delta is positive, sell dw -> buy ul
    // PUT dw sell, order is negative, delta is negative, sell dw -> sell ul
    BigDecimal(qty * signedDelta * -1).setScale(0, RoundingMode.HALF_EVEN).toLong // positive = buy ul, negative = sell ul
  }

  def getPutOrCall(inDe: InstrumentDescriptor): Option[PutCall] =
    if(inDe.getName.length < 5) None else {
      inDe.getName.toList(5) match {
        case 'C' => Some(CALL)
        case 'P' => Some(PUT)
        case _ => None
      }
    }

  def getUlProjectedPrice(ulInst: InstrumentDescriptor, totalResidual: Long): Either[Error, Double] =
    getProjectedPrice(ulInst)
      .map(BigDecimal(_))
      .map(Algo.getPriceAfterTicks(if(totalResidual < 0) true else false, _))
      .map(_.toDouble)
    .match {
      case Some(value) => Right(value)
      case None => Left(Error.MarketError(s"Underlying price not found for ${ulInst.getName}"))
    }


  def getDelta(dwId: String): Option[Double] = source[Pricing].get(dwId, "DEFAULT").latest.map(_.delta) // negative = put //Nop will confirm DEFAULT or DYNAMIC or both

  def main[F[_]: Monad]: EitherT[F, guardian.Error, Order] =
    for {
      dwList <- EitherT.rightT(getDwList(dictionaryService, ulId))
      dwProjectedPriceList <- EitherT.rightT(dwList.map(getProjectedPrice))
      bestBidPriceList <- EitherT.rightT(dwList.map(getOwnBestBidPrice))
      bestAskPriceList <- EitherT.rightT(dwList.map(getOwnBestAskPrice))
      deltaList <- EitherT.rightT(dwList.map(p => getDelta(p.getUniqueId)))
      dwPutCallList <- EitherT.rightT(dwList.map(getPutOrCall))
      signedDeltaList <- EitherT.rightT(
        (deltaList, dwPutCallList)
          .zipped
          .toList
          .map{
            case (Some(delta), Some(CALL)) => 1 * delta
            case (Some(delta), Some(PUT)) => -1 * delta
            case _ => 0
          }
      )
      totalResidual <- EitherT.rightT(
        (bestBidPriceList, bestAskPriceList, dwProjectedPriceList)
          .zipped
          .toList
          .zip(signedDeltaList)
          .map{
            case ((Some(a),Some(b),Some(c)),d) => (a,b,c,d)
            case ((_,_,_),d) => (0,0,0,d)
          }
          .zip(dwList)
          .map{
            case ((a,b,c,d),e) => (a,b,c,d,e.getUniqueId)
          }
          .map(p => calcUlQtyPreResidual(p._1, p._2, p._3, p._4, p._5))
          .sum + dailyResidual
      )
      ulProjectedPrice <- EitherT.fromEither(getUlProjectedPrice(ulInstrument, totalResidual))
      order = Algo.createOrder(totalResidual, ulProjectedPrice, if(totalResidual > 0) BuySell.BUY else BuySell.SELL, UUID.randomUUID().toString)
    } yield order

  onMessage { case Load => /*
        Get all derivative warrants
        - What is "SET" ? exchange name
       */ val dwList: List[InstrumentDescriptor] = dictionaryService.getDictionary.getProducts(null).values().asScala.map(p => p.asInstanceOf[Derivative]).filter(d => d.getUlId == ulId && d.getProductType == ProductTypes.WARRANT).map(p => getService[InstrumentInfoService].getInstrumentByUniqueId("SET", p.getId)).toList


       /*
        Get a particular dw
       */

    val dwId = "PTT24CA"
    val dwPTT24CA = dwList.find(p => p.getUniqueId == dwId)


    /*
        Get projected price, projected volume of a dw
       */

    val ssDwPTT24CA = source[Summary].get(dwPTT24CA.get) // .map(_.modeStr.get)
    val ssDwPTT24CA_ProjectedPrice = ssDwPTT24CA.latest.get.theoOpenPrice
    val ssDwPTT24CA_ProjectedVolume = ssDwPTT24CA.latest.get.theoOpenVolume


  }
}

      /*
      Get own quantity
      Confirm with others
     */


      /*
      Get someone else's volume
        if we know otherPeopleVol we will find ourOwnMatchedVol



      /*
        Get delta
        - Is the symbol (dwId) correct ?
      */
      val ssDwPTT24CA_pricingSource = source[Pricing].get(dwId,"DEFAULT")
      val delta = ssDwPTT24CA_pricingSource.latest.get.delta

    /*

      Get residual
      PTT
      PTT24C2201A CallDW Delta 0.01  Sell ProjDWMatchQty  20,000  ProjDWMatchQty*delta = Buy UL 200
      PTT24C2201B CallDW Delta 0.02  Buy ProjDWMatchQty  20,000  ProjDWMatchQty*delta = Sell UL -400
      PTT24P2201A PutDW Delta -0.01 Sell ProjDWMatchQty  10,000  ProjDWMatchQty*delta = Sell UL -100
      PTT24P2201B PutDW Delta -0.04 Buy ProjDWMatchQty  10,000  ProjDWMatchQty*delta = Buy UL 400

      residual sumDW = (200+-400+-100+400) = 100 Buy UL 100

      residual portfolio can get from portfolio Buy UL 300

      residual sumDW + residual portfolio Send the order

     */



      /*
        Revisit:
        Get direction (ask / bid)

          Method 1:
          Projected price <= automation best bid : CALL Sell UL
          Projected price >= automation best ask : CALL Buy UL
          Projected price <= automation best bid : PUTDW Buy UL
          Projected price >= automation best ask : PUTDW Sell UL

          Method2:
          Look at the name of the dw market

       */





    /*
       At this point we have the quantity of the order we need to send to underlying market
       - DwData :
          dw projected price,
          dw projected volume,
          ownVolume,
          hedge ratio,
          residual,
          call or put
     */


      /*
        Get underlying projected price
       */
      val ssPTT = source[Summary].get(ulInstrument)
      val ssPTT_ProjectedPrice =  ssPTT.latest.get.theoOpenPrice.get


      /*
        Get the new ask/bid price counted 5 ticks from projected price
        - Check the behavior
          it should "return 32 when price is 30.75 ticked down 5 steps"
          it should "return 24.70 when price is 25.50 ticked down 5 steps"
          it should "return 101.5 when price is 99.5 ticked up 5 steps"
          it should "return 98.25 when price is 99.5 ticked down 5 steps"
       */
      val sellingPrice = getPriceAfterTicks(5, ssPTT_ProjectedPrice)
      def getPriceAfterTicks(ticks: Int, price: Double): Double = ???

//      def SETChkSpread(refPrice:Double): Double ={
//        var chkSpread:Double = 0
//        if (refPrice>=0 && refPrice < 2) {
//          chkSpread = 0.01
//        } else if (refPrice>=2 && refPrice<5){
//          chkSpread = 0.02
//        } else if (refPrice>=5 && refPrice<10){
//          chkSpread = 0.05
//        } else if (refPrice>=10 && refPrice<25){
//          chkSpread = 0.1
//        } else if (refPrice>=25 && refPrice<100){
//          chkSpread = 0.25
//        } else if (refPrice>=100 && refPrice<200){
//          chkSpread = 0.5
//        } else if (refPrice>=200 && refPrice<400){
//          chkSpread = 1
//        } else if (refPrice>=400){
//          chkSpread = 2
//        } else {
//          chkSpread = 0
//        }
//         chkSpread
//      }


    /*
      Check portfolio's position
      - Check ulId
     */

      val pttPortfolio = source[RiskPositionDetailsContainer].get(portfolioId,ulId, true)
      val pttPosition = pttPortfolio.latest.get.getTotalPosition.getNetQty

      /*
         Main calculation happens here
        Use The price and quantity to create orders to send to underlying market
       */

     /*
         Send Limit order(s)
         - Check instrument
         - portfolioId
     */
//      sendLimitOrder(instrument, BuySell.BUY, qty, roundPrice(instrument,price), (o, i) => {
//        o.setCustomField(ReferenceMarketDataField.PORTFOLIO, portfolioId)
//        o
//      })
    case Start =>
  }
