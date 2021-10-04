package mrt

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
import guardian.{Algo, Error}
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
  val ulId: String = ulInstrument.getUniqueId
  val context      = "DEFAULT"
  val lotSize: Int = 100

  var algo: Option[Algo[Id]] = None

  import algotrader.api.source.summary._

  def getDwList(
      ds: IDictionaryProvider,
      ulId: String,
      exchangeName: String = "SET-EMAPI-HMM-PROXY"
  ): List[InstrumentDescriptor] =
    ds.getDictionary
      .getProducts(null)
      .values()
      .asScala
      .filter(d => d.getProductType == ProductTypes.WARRANT)
      .map(p => p.asInstanceOf[Derivative])
      .filter(d => d.getUlId == ulId)
      .map(p =>
        getService[InstrumentInfoService]
          .getInstrumentByUniqueId(exchangeName, p.getId)
      )
      .toList

  def getProjectedPrice(inDe: InstrumentDescriptor): Option[Double] =
    source[Summary].get(inDe).latest.flatMap(_.theoOpenPrice)

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

  def validatePositiveAmount(order: Order): Either[Error, Unit] =
    Either.cond(order.getQuantityL > 0, (), Error.StateError("Pre-process order qty cannot be negative"))

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

  def preProcess[F[_]: Monad]: EitherT[F, Error, Order] =
    for {
      dwList <- EitherT.rightT[F, guardian.Error](getDwList(dictionaryService, ulId))
      _ = log.warn(s"1. $dwList")
      dwProjectedPriceList <- EitherT.rightT[F, guardian.Error](dwList.map(getProjectedPrice))
      _ = log.warn(s"2. $dwProjectedPriceList")
      bestBidPriceList <- EitherT.rightT[F, guardian.Error](dwList.map(getOwnBestBidPrice))
      _ = log.warn(s"3. $bestBidPriceList")
      bestAskPriceList <- EitherT.rightT[F, guardian.Error](dwList.map(getOwnBestAskPrice))
      _ = log.warn(s"4. $bestAskPriceList")
      deltaList <- EitherT.rightT[F, guardian.Error](dwList.map(p => getDelta(p.getUniqueId)))
      _ = log.warn(s"5. $deltaList")
      dwPutCallList <- EitherT.rightT[F, guardian.Error](dwList.map(getPutOrCall))
      _ = log.warn(s"6. $dwPutCallList")
      pointValue <- EitherT.rightT[F, guardian.Error](getPointValue(hedgeInstrument))
      _ = log.warn(s"7. $pointValue")
      absoluteResidual <- EitherT.rightT[F, guardian.Error](getAbsoluteResidual(pointValue, ulId))
      _ = log.warn(s"8. $absoluteResidual")
      signedDeltaList <- EitherT.rightT[F, guardian.Error](
        (deltaList, dwPutCallList).zipped.toList
          .map {
            case (Some(delta), Some(CALL)) => 1 * delta
            case (Some(delta), Some(PUT))  => -1 * delta
            case _                         => 0
          }
      )
      _ = log.warn(s"9. $signedDeltaList")
      partialResidual <- EitherT.rightT[F, guardian.Error](
        (bestBidPriceList, bestAskPriceList, dwProjectedPriceList).zipped.toList
          .zip(signedDeltaList)
          .map {
            case ((Some(a), Some(b), Some(c)), d) => (a, b, c, d)
            case ((_, _, _), d)                   => (0.0, 0.0, 0.0, d)
          }
          .zip(dwList)
          .map {
            case ((a, b, c, d), e) => (a, b, c, d, e.getUniqueId)
          }
          .map(p => calcUlQtyPreResidual(p._1, p._2, p._3, p._4, p._5))
          .sum
      )
      _             = log.warn(s"10. $partialResidual")
      totalResidual = partialResidual + absoluteResidual.getOrElse(BigDecimal("0")).toLong
      _             = log.warn(s"11. $totalResidual")
      direction     = if (totalResidual < 0) Direction.SELL else Direction.BUY
      _             = log.warn(s"12. $direction")
      hzDirection   = if (direction == Direction.SELL) BuySell.SELL else BuySell.BUY
      _             = log.warn(s"13. $hzDirection")
      ulProjectedPrice <- EitherT.fromEither(getUlProjectedPrice(ulInstrument, direction))
      _                = log.warn(s"14. $ulProjectedPrice")
      absTotalResidual = Math.abs(totalResidual)
      _                = log.warn(s"15. $absTotalResidual")
      order            = Algo.createOrder(absTotalResidual, ulProjectedPrice.toDouble, hzDirection, CustomId.generate)
      _                = log.warn(s"16. $order")
      _ <- EitherT.fromEither(validatePositiveAmount(order))
    } yield order

  def initAlgo[F[_]: Applicative: Monad](portfolioQty: Long): F[Algo[F]] =
    Algo(
      ulId,
      lotSize,
      portfolioQty,
      preProcess = preProcess[F],
      sendOrder = sendOrderAction,
      logAlert = log.warn,
      logInfo = log.info,
      logError = log.error
    )

  onOrder {
    case Nak(t) =>
      algo.map(_.handleOnOrderNak(CustomId.fromOrder(t.getOrderCopy), "Nak signal / order rejected"))

    case Ack(t) =>
      algo.map(_.handleOnOrderAck(CustomId.fromOrder(t.getOrderCopy)))
  }

  onMessage {
    case Load =>
      log.info("Agent Loading")

      val sourceSummary = source[Summary].get(ulInstrument)

      sourceSummary
        .filter(p =>
          p.theoOpenPrice.isDefined || p.theoOpenVolume.isDefined || p.buyPrice.isDefined || p.buyQty.isDefined || p.sellPrice.isDefined || p.sellQty.isDefined
            || p.last.isDefined || p.closePrevious.isDefined
        )
        .onUpdate(_ => algo.map(_.handleOnSignal()))

      source[RiskPositionDetailsContainer].get(portfolioId, ulId, true).onUpdate(_ => algo.map(_.handleOnSignal()))

      getDwList(dictionaryService, ulId)
        .map(p => source[AutomatonStatus].get("SET-EMAPI-HMM-PROXY", context, p.getUniqueId, "REFERENCE"))
        .foreach(_.onUpdate(_ => algo.map(_.handleOnSignal())))

      source[Summary].get(ulInstrument).map(_.modeStr.get) onUpdate {
        case "Startup" =>
          algo = None

        case "Pre-Open1" =>
          algo = Some(initAlgo[Id](getPortfolioQty(ulId).getOrElse(0.0).toLong))

        case "Open1" =>
        case "Intermission" =>
          algo = None

        case "Pre-Open2" =>
          algo = Some(initAlgo[Id](getPortfolioQty(ulId).getOrElse(0.0).toLong))

        case "Open2" =>
        case "Pre-close" =>
          algo = Some(initAlgo[Id](getPortfolioQty(ulId).getOrElse(0.0).toLong))

        case "OffHour" =>
          algo = None

        case "Closed" =>
          algo = None

        case "Closed2" =>
          algo = None

        case "AfterMarket" =>
          algo = None

        case "CIRCUIT_BREAKER" =>
          algo = None

        case "Pre-OpenTemp" =>
          algo = None

        case _ =>
          algo = None
      }

    case Start => log.info("Agent starting")
  }
}
