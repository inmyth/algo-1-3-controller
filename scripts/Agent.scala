import horizontrader.services.instruments.InstrumentDescriptor
import algotrader.api.NativeTradingAgent
import algotrader.api.Messages._
import com.ingalys.imc.BuySell
import cats.{Applicative, Id, Monad}
import cats.data.EitherT
import com.hsoft.datamaster.product.{Derivative, ProductTypes}
import com.hsoft.hmm.api.source.automatonstatus.AutomatonStatus
import com.hsoft.hmm.api.source.position.{RiskPositionByUlSourceBuilder, RiskPositionDetailsSourceBuilder}
import com.hsoft.hmm.api.source.pricing.{Pricing, PricingSourceBuilder}
import com.hsoft.hmm.posman.api.position.container.{RiskPositionByULContainer, RiskPositionDetailsContainer}
import com.ingalys.imc.order.Order
import com.ingalys.imc.summary.Summary
import guardian.Algo.{xxx2, xxx3}
import guardian.Manager
//import guardian.Algo.xxx2
import guardian.{
  Algo,
  Error,
  LiveOrdersInMemInterpreter,
  PendingCalculationInMemInterpreter,
  PendingOrdersInMemInterpreter,
  UnderlyingPortfolioInterpreter
}
import guardian.Entities.PutCall.{CALL, PUT}
import guardian.Entities.{CustomId, Direction, OrderAction, PutCall}
import horizontrader.plugins.hmm.connections.service.IDictionaryProvider
import horizontrader.services.instruments.InstrumentInfoService

import scala.collection.JavaConverters._
import scala.language.higherKinds
import scala.math.BigDecimal.RoundingMode

trait Agent extends NativeTradingAgent {
  val portfolioId: String
  val ulInstrument: InstrumentDescriptor
  val hedgeInstrument: InstrumentDescriptor //PTT@XBKK ?? Nop will find a way // String => SET-EMAPI-HMM-PROXY|ADVANC@XBKK
  val dictionaryService: IDictionaryProvider
//  val ulId: String = ulInstrument.getUniqueId // CAUSES ERROR

  import algotrader.api.source.summary._

  def getDwList(ds: IDictionaryProvider, ulId: String, exchangeName: String = "SET"): List[InstrumentDescriptor] =
    ds.getDictionary
      .getProducts(null)
      .values()
      .asScala
      .map(p => p.asInstanceOf[Derivative])
      .filter(d => d.getUlId == ulId && d.getProductType == ProductTypes.WARRANT)
      .map(p =>
        getService[InstrumentInfoService]
          .getInstrumentByUniqueId(exchangeName, p.getId)
      )
      .toList

  def getProjectedPrice(inDe: InstrumentDescriptor): Option[Double] =
    source[Summary].get(inDe).latest.flatMap(_.theoOpenPrice)

  def getProjectedVolume(inDe: InstrumentDescriptor): Option[Long] =
    source[Summary].get(inDe).latest.flatMap(_.theoOpenVolume)

  def getOwnBestBidPrice(inDe: InstrumentDescriptor): Option[Double] =
    source[Summary].get(inDe).latest.flatMap(_.buyPrice)

  def getOwnBestAskPrice(inDe: InstrumentDescriptor): Option[Double] =
    source[Summary].get(inDe).latest.flatMap(_.sellPrice)

  def getPortfolioQty(ulId: String): Option[Double] =
    source[RiskPositionDetailsContainer].get(portfolioId, ulId, true).latest.map(_.getTotalPosition.getNetQty)

  def calcUlQtyPreResidual(
      ownBestBid: Double,
      ownBestAsk: Double,
      marketProjectedPrice: Double,
      signedDelta: Double,
      dwId: String,
      context: String = "DEFAULT"
  ): Long = {
    val autoSource       = source[AutomatonStatus].get("SET-EMAPI-HMM-PROXY", context, dwId, "REFERENCE")
    val bdProjectedPrice = BigDecimal(marketProjectedPrice).setScale(2, RoundingMode.HALF_EVEN)
    val bdOwnBestBid     = BigDecimal(ownBestBid).setScale(2, RoundingMode.HALF_EVEN)
    val bdOwnBestAsk     = BigDecimal(ownBestAsk).setScale(2, RoundingMode.HALF_EVEN)

    val qty: Long = if (bdProjectedPrice <= bdOwnBestBid) {
      context match {
        case "DEFAULT" =>
          autoSource.latest
            .map(
              _.buyStatuses
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
            .map(_.buyStatuses(0))
            .map(p => {
              val v = BigDecimal(p.priceOnMarket).setScale(2, RoundingMode.HALF_EVEN)
              if (bdProjectedPrice <= v && v <= bdOwnBestBid) p.qtyOnMarketL else 0L
            })
            .getOrElse(0L)
        case _ => 0L
      }
    } else if (bdProjectedPrice >= bdOwnBestAsk) {
      context match {
        case "DEFAULT" =>
          autoSource.latest
            .map(
              _.sellStatuses
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
            .map(_.sellStatuses(0))
            .map(p => {
              val v = BigDecimal(p.priceOnMarket).setScale(2, RoundingMode.HALF_EVEN)
              if (bdProjectedPrice >= v && v >= bdOwnBestAsk) p.qtyOnMarketL else 0L
            })
            .getOrElse(0L) * -1
        case _ => 0L
      }
    } else {
      0L // own orders are not matched
    }
    // CALL dw buy, order is positive , delta is positive, buy dw-> sell ul
    // PUT dw, buy, order is positive, delta is negative, buy dw -> buy ul
    // CALL dw sell, order is negative, delta is positive, sell dw -> buy ul
    // PUT dw sell, order is negative, delta is negative, sell dw -> sell ul
    BigDecimal(qty * signedDelta * -1)
      .setScale(0, RoundingMode.HALF_EVEN)
      .toLong // positive = buy ul, negative = sell ul
  }

  def getPutOrCall(inDe: InstrumentDescriptor): Option[PutCall] =
    if (inDe.getName.length < 5) None
    else {
      inDe.getName.toList(5) match {
        case 'C' => Some(CALL)
        case 'P' => Some(PUT)
        case _   => None
      }
    }

  def getUlProjectedPrice(ulInst: InstrumentDescriptor, direction: Direction): Either[Error, BigDecimal] =
    getProjectedPrice(ulInst)
      .map(BigDecimal(_))
      .map(p => Algo.getPriceAfterTicks(if (direction == Direction.BUY) true else false, p)) match {
      case Some(value) => Right(value)
      case None        => Left(Error.MarketError(s"Underlying price not found for ${ulInst.getName}"))
    }

  def getDelta(dwId: String): Option[Double] =
    source[Pricing]
      .get(dwId, "DEFAULT")
      .latest
      .map(_.delta) // negative = put //Nop will confirm DEFAULT or DYNAMIC or both

  def getPointValue(hedgeInDe: InstrumentDescriptor): Double = hedgeInDe.getPointValue.doubleValue()

  def getAbsoluteResidual(pointValue: Double, ulId: String): Option[BigDecimal] =
    source[RiskPositionByULContainer]
      .get(portfolioId, ulId, true)
      .latest
      .map(p => BigDecimal(p.getTotalPosition.getDeltaCashUlCurr / p.getTotalPosition.getUlSpot / pointValue))

  def sendOrderAction(act: OrderAction): Order =
    act match {
      case OrderAction.InsertOrder(order) =>
        sendLimitOrder(
          instrument = ulInstrument,
          way = order.getBuySell,
          quantity = order.getQuantityL,
          price = order.getPrice,
          orderModifier = (o, _) => {
            o.setCustomField(CustomId.field, CustomId.fromOrder(order).v)
            o
          }
        )
      case OrderAction.UpdateOrder(order) =>
        updateOrder(
          instrument = ulInstrument,
          order = order
        )
      case OrderAction.CancelOrder(order) =>
        deleteOrder(
          instrument = ulInstrument,
          order = order
        )
    }

  xxx3(ulInstrument)
//  val manager: Manager = Manager.init(
//    getDwList = getDwList,
//    getDwProjectedPrice = getProjectedPrice,
//    getOwnBestAskPrice = getOwnBestAskPrice,
//    calcUlQtyPreResidual = calcUlQtyPreResidual,
//    getPutOrCall = getPutOrCall,
//    getUlProjectedPrice = getUlProjectedPrice,
//    getDelta = getDelta,
//    getPointValue = getPointValue,
//    getAbsoluteResidual = getAbsoluteResidual,
//    ulInstrument = ulInstrument,
//    dictionaryService = dictionaryService,
//    hedgeInstrument = hedgeInstrument,
//    sendOrder = sendOrderAction,
//    logAlert = log.warn,
//    logInfo = log.info,
//    logError = log.error
//  )

//  source[Summary].get(ulInstrument).map(_.modeStr.get) onUpdate {
//    case "Startup" =>
//      algo = None
//
//    case "Pre-Open1" =>
//      algo = Some(initAlgo[Id])
//      algo.map(_.handleOnLoad(ulId, getPortfolioQty.getOrElse(0.0).toLong))
//
//    case "Open1" =>
//      algo = None
//
//    case "Intermission" =>
//      algo = None
//
//    case "Pre-Open2" =>
//      algo = Some(initAlgo[Id])
//      algo.map(_.handleOnLoad(ulId, getPortfolioQty.getOrElse(0.0).toLong))
//
//    case "Open2" =>
//      algo = None
//
//    case "Pre-close" =>
//      algo = Some(initAlgo[Id])
//      algo.map(_.handleOnLoad(ulId, getPortfolioQty.getOrElse(0.0).toLong))
//
//    case "OffHour" =>
//      algo = None
//
//    case "Closed" =>
//      algo = None
//
//    case "Closed2" =>
//      algo = None
//
//    case "AfterMarket" =>
//      algo = None
//
//    case "CIRCUIT_BREAKER" =>
//      algo = None
//
//    case "Pre-OpenTemp" =>
//      algo = None
//
//    case _ =>
//      algo = None
//
//  }
//
//  // This is the main function
//  source[Summary].get(ulInstrument).onUpdate(_ => algo.map(_.handleOnSignal()))
//
//  onOrder {
//    case Nak(t) =>
//      algo.map(_.handleOnOrderNak(CustomId.fromOrder(t.getOrderCopy), "Nak signal / order rejected"))
//
//    case Ack(t) =>
//      algo.map(_.handleOnOrderAck(CustomId.fromOrder(t.getOrderCopy)))
//  }
//
  onMessage {

    case Load  => xxx2(log.error)
    case Start =>
  }
}
