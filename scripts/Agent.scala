package mrt

import horizontrader.services.instruments.InstrumentDescriptor
import algotrader.api.NativeTradingAgent
import algotrader.api.Messages._
import algotrader.api.source.Source
import algotrader.api.source.summary._
import com.ingalys.imc.BuySell
import cats.{Applicative, Id, Monad}
import cats.data.EitherT
import com.hsoft.datamaster.product.{Derivative, ProductTypes}
import com.hsoft.hmm.api.source.automatonstatus.{AutomatonStatus, AutomatonStatusSourceProvider}
import com.hsoft.hmm.api.source.position.details.risk.RiskPositionDetailsSourceProvider
import com.hsoft.hmm.api.source.position.ul.risk.RiskPositionByULSourceProvider
import com.hsoft.hmm.api.source.position.{RiskPositionByUlSourceBuilder, RiskPositionDetailsSourceBuilder}
import com.hsoft.hmm.api.source.pricing.{Pricing, PricingSourceBuilder, PricingSourceProvider}
import com.hsoft.hmm.posman.api.position.container.{RiskPositionByULContainer, RiskPositionDetailsContainer}
import com.hsoft.scenario.status.ScenarioStatus
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
  val hedgeInstrument: InstrumentDescriptor
  val dictionaryService: IDictionaryProvider
  val context                              = "DEFAULT"
  val lotSize: Int                         = 100
  val exchange                             = "SET-EMAPI-HMM-PROXY"
  var algo: Option[Algo[Id]]               = None
  var ulProjectedPrice: Option[Double]     = None
  var dwMap: Map[String, DW]               = Map.empty
  var absoluteResidual: Option[BigDecimal] = None
  var pointValue: Option[Double]           = None
  var portfolioQty: Option[Long]           = None

  def initAlgo[F[_]: Applicative: Monad](ulId: String, portfolioQty: Long): F[Algo[F]] =
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

  def calcUlQtyPreResidual(
      buyStatuses: Seq[ScenarioStatus],
      sellStatuses: Seq[ScenarioStatus],
      ownBestBid: Double,
      ownBestAsk: Double,
      marketProjectedPrice: Double,
      signedDelta: Double,
      context: String = "DEFAULT"
  ): Long = {
    val bdProjectedPrice = BigDecimal(marketProjectedPrice).setScale(2, RoundingMode.HALF_EVEN)
    val bdOwnBestBid     = BigDecimal(ownBestBid).setScale(2, RoundingMode.HALF_EVEN)
    val bdOwnBestAsk     = BigDecimal(ownBestAsk).setScale(2, RoundingMode.HALF_EVEN)

    val qty: Long = if (bdProjectedPrice <= bdOwnBestBid) {
      context match {
        case "DEFAULT" =>
          buyStatuses
            .filter(p => {
              val v = BigDecimal(p.priceOnMarket).setScale(2, RoundingMode.HALF_EVEN)
              bdProjectedPrice <= v && v <= bdOwnBestBid
            })
            .map(_.qtyOnMarketL)
            .sum
        case "DYNAMIC" =>
          val p = buyStatuses(0)
          val v = BigDecimal(p.priceOnMarket).setScale(2, RoundingMode.HALF_EVEN)
          if (bdProjectedPrice <= v && v <= bdOwnBestBid) p.qtyOnMarketL else 0L
      }
    } else if (bdProjectedPrice >= bdOwnBestAsk) {
      -1 * context match {
        case "DEFAULT" =>
          sellStatuses
            .filter(p => {
              val v = BigDecimal(p.priceOnMarket).setScale(2, RoundingMode.HALF_EVEN)
              bdProjectedPrice >= v && v >= bdOwnBestAsk
            })
            .map(_.qtyOnMarketL)
            .sum
        case "DYNAMIC" =>
          val p = sellStatuses(0)
          val v = BigDecimal(p.priceOnMarket).setScale(2, RoundingMode.HALF_EVEN)
          if (bdProjectedPrice >= v && v >= bdOwnBestAsk) p.qtyOnMarketL else 0L
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

  def getPutOrCall(name: String): Option[PutCall] =
    if (name.length < 5) None
    else {
      name.toList(5) match {
        case 'C' => Some(CALL)
        case 'P' => Some(PUT)
        case _   => None
      }
    }

  def shiftUlProjectedPrice(price: Double, direction: Direction): BigDecimal =
    Algo.getPriceAfterTicks(if (direction == Direction.BUY) true else false, BigDecimal(price))

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
      _ <-
        EitherT.fromEither(Either.cond(ulProjectedPrice.isDefined, (), Error.MarketError("Underlying price is empty")))
      _ <- EitherT.fromEither(Either.cond(pointValue.isDefined, (), Error.MarketError("Point value is empty")))
      _ <-
        EitherT.fromEither(Either.cond(absoluteResidual.isDefined, (), Error.MarketError("Absolute residual is empty")))
      dwList <- EitherT.rightT(dwMap.values.filter(p => {
        p.projectedPrice.isDefined && p.putCall.isDefined
      }))
      dwSignDeltaList <- EitherT.rightT[F, guardian.Error](
        dwList.map(p => {
          val signedDelta = (p.putCall, p.delta) match {
            case (Some(CALL), Some(delta)) => 1 * delta
            case (Some(PUT), Some(delta))  => -1 * delta
            case _                         => 0
          }
          p.copy(delta = Some(signedDelta))
        })
      )
      partialResidual <- EitherT.rightT[F, guardian.Error](
        dwSignDeltaList
          .map(p =>
            calcUlQtyPreResidual(
              buyStatuses = p.buyStatuses,
              sellStatuses = p.sellStatuses,
              ownBestBid = p.bestBidPrice.getOrElse(0.0),
              ownBestAsk = p.bestAskPrice.getOrElse(Int.MaxValue.toDouble),
              marketProjectedPrice = p.projectedPrice.get,
              signedDelta = p.delta.get,
              context = context
            )
          )
          .sum
      )
      totalResidual = partialResidual + absoluteResidual.get.toLong
      _             = log.warn(s"11. $totalResidual")
      direction     = if (totalResidual < 0) Direction.SELL else Direction.BUY
      _             = log.warn(s"12. $direction")
      hzDirection   = if (direction == Direction.SELL) BuySell.SELL else BuySell.BUY
      _             = log.warn(s"13. $hzDirection")
      ulShiftedProjectedPrice <- EitherT.rightT[F, Error](shiftUlProjectedPrice(ulProjectedPrice.get, direction))
      _                = log.warn(s"15. $ulShiftedProjectedPrice")
      absTotalResidual = Math.abs(totalResidual)
      _                = log.warn(s"16. $absTotalResidual")
      order            = Algo.createOrder(absTotalResidual, ulShiftedProjectedPrice.toDouble, hzDirection, CustomId.generate)
      _                = log.warn(s"17. $order")
      _ <- EitherT.fromEither(validatePositiveAmount(order))
    } yield order

  onOrder {
    case Nak(t) =>
      algo.map(_.handleOnOrderNak(CustomId.fromOrder(t.getOrderCopy), "Nak signal / order rejected"))

    case Ack(t) =>
      algo.map(_.handleOnOrderAck(CustomId.fromOrder(t.getOrderCopy)))
  }

  case class DW(
      uniqueId: String,
      projectedPrice: Option[Double] = None,
      bestBidPrice: Option[Double] = None,
      bestAskPrice: Option[Double] = None,
      delta: Option[Double] = None,
      putCall: Option[PutCall] = None,
      sellStatuses: Seq[ScenarioStatus] = Seq.empty,
      buyStatuses: Seq[ScenarioStatus] = Seq.empty
  )

  onMessage {

    case Load =>
      log.info("Agent Loading")
      val ulId     = ulInstrument.getUniqueId
      val sSummary = source[Summary]

      pointValue = Option(hedgeInstrument.getPointValue).map(_.doubleValue())

      sSummary
        .get(ulInstrument)
        .filter(s => s.theoOpenPrice.isDefined)
        .onUpdate(s => {
          ulProjectedPrice = s.theoOpenPrice
          algo.map(_.handleOnSignal())
          log.info(s"ProjPx= " + s.theoOpenPrice.get)
        })

      source[RiskPositionDetailsContainer]
        .get(portfolioId, ulInstrument.getUniqueId, true)
        .filter(p => Option(p.getTotalPosition).isDefined)
        .onUpdate(p => {
          portfolioQty = Some(p.getTotalPosition.getNetQty.toLong)

          algo = Some(initAlgo[Id](ulId, portfolioQty.get))
          algo.map(_.handleOnSignal())
        })

      source[RiskPositionDetailsContainer]
        .get(portfolioId, ulInstrument.getUniqueId, true)
        .filter(p => Option(p.getTotalPosition).isDefined)
        .onUpdate(p => {
          if (pointValue.isDefined)
            absoluteResidual =
              Some(BigDecimal(p.getTotalPosition.getDeltaCashUlCurr / p.getTotalPosition.getUlSpot / pointValue.get))
        })

      dictionaryService.getDictionary
        .getProducts(null)
        .values()
        .asScala
        .filter(p => p.getProductType == ProductTypes.WARRANT)
        .map(p => p.asInstanceOf[Derivative])
        .filter(d => d.getUlId == ulId)
        .foreach(d => {
          val dwInstrument = getService[InstrumentInfoService].getInstrumentByUniqueId(exchange, d.getId)
          dwMap += (dwInstrument.getUniqueId -> DW(
            uniqueId = dwInstrument.getUniqueId,
            putCall = getPutOrCall(dwInstrument.getName)
          ))

          log.info(s"Subscribing to Executions on [${d}]")
          log.info(dwMap)

          sSummary
            .get(dwInstrument)
            .filter(s => s.theoOpenPrice.isDefined)
            .onUpdate(s => {
              val x = dwMap
                .getOrElse(dwInstrument.getUniqueId, DW(dwInstrument.getUniqueId))
                .copy(projectedPrice = s.theoOpenPrice)
              dwMap += (x.uniqueId -> x)
              algo.map(_.handleOnSignal())
              log.info(s"DW ProjPx= ${dwInstrument.getUniqueId} ${s.theoOpenPrice}")
            })
          sSummary
            .get(dwInstrument)
            .filter(s => s.buyPrice.isDefined)
            .onUpdate(s => {
              val x =
                dwMap.getOrElse(dwInstrument.getUniqueId, DW(dwInstrument.getUniqueId)).copy(bestBidPrice = s.buyPrice)
              dwMap += (x.uniqueId -> x)
              algo.map(_.handleOnSignal())
              log.info(s"DW ProjPx= ${dwInstrument.getUniqueId} ${s.buyPrice}")
            })
          sSummary
            .get(dwInstrument)
            .filter(s => s.sellPrice.isDefined)
            .onUpdate(s => {
              val x =
                dwMap.getOrElse(dwInstrument.getUniqueId, DW(dwInstrument.getUniqueId)).copy(bestAskPrice = s.sellPrice)
              dwMap += (x.uniqueId -> x)
              algo.map(_.handleOnSignal())
              log.info(s"DW ProjPx= ${dwInstrument.getUniqueId} ${s.sellPrice}")
            })
          source[Pricing]
            .get(dwInstrument.getUniqueId, "DEFAULT")
            .filter(s => Option(s.delta).isDefined)
            .onUpdate(s => {
              val x =
                dwMap.getOrElse(dwInstrument.getUniqueId, DW(dwInstrument.getUniqueId)).copy(delta = Some(s.delta))
              dwMap += (x.uniqueId -> x)
              algo.map(_.handleOnSignal())
            })
          source[AutomatonStatus]
            .get(exchange, context, dwInstrument.getUniqueId, "REFERENCE")
            .onUpdate(s => {
              val x = dwMap
                .getOrElse(dwInstrument.getUniqueId, DW(dwInstrument.getUniqueId))
                .copy(buyStatuses = s.buyStatuses.toList)
              dwMap += (x.uniqueId -> x)
              algo.map(_.handleOnSignal())
            })
          source[AutomatonStatus]
            .get(exchange, context, dwInstrument.getUniqueId, "REFERENCE")
            .onUpdate(s => {
              val x = dwMap
                .getOrElse(dwInstrument.getUniqueId, DW(dwInstrument.getUniqueId))
                .copy(sellStatuses = s.sellStatuses.toList)
              dwMap += (x.uniqueId -> x)
              algo.map(_.handleOnSignal())
            })
        })

      sSummary.get(ulInstrument).map(_.modeStr.get) onUpdate {
        case "Startup" =>
          algo = None

        case "Pre-Open1" =>
//          algo = Some(initAlgo[Id](getPortfolioQty(sRiskPosition, ulId).getOrElse(0.0).toLong))

        case "Open1" =>
        case "Intermission" =>
          algo = None

        case "Pre-Open2" =>
//          algo = Some(initAlgo[Id](getPortfolioQty(sRiskPosition, ulId).getOrElse(0.0).toLong))

        case "Open2"     =>
        case "Pre-close" =>
//          algo = Some(initAlgo[Id](getPortfolioQty(sRiskPosition, ulId).getOrElse(0.0).toLong))

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
