package mrt

import horizontrader.services.instruments.InstrumentDescriptor
import algotrader.api.{NativeTradingAgent, TrxMessages}
import algotrader.api.Messages._
import algotrader.api.source.summary._
import com.ingalys.imc.BuySell
import cats.{Applicative, Id, Monad}
import cats.data.EitherT
import com.hsoft.datamaster.product.{Derivative, ProductTypes}
import com.hsoft.hmm.api.source.automatonstatus.AutomatonStatus
import com.hsoft.hmm.api.source.position.{RiskPositionByUlSourceBuilder, RiskPositionDetailsSourceBuilder}
import com.hsoft.hmm.api.source.pricing.{Pricing, PricingSourceBuilder}
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
  val portfolioId: String                    //JV
  val hedgePortfolio: String                 //9901150 Buy Sell UL
  val ulInstrument: InstrumentDescriptor     //RBF@XBKK
  val hedgeInstrument: InstrumentDescriptor  //RBF@XBKK
  val dictionaryService: IDictionaryProvider // List of RBF@XBKK DW
  val context                          = "DEFAULT"
  val lotSize: Int                     = 100
  val exchange                         = "SET-EMAPI-HMM-PROXY"
  var algo: Option[Algo[Id]]           = None
  var ulProjectedPrice: Option[Double] = None
  var dwMap: Map[String, DW]           = Map.empty
  var absoluteResidual: BigDecimal     = BigDecimal("0")
  var pointValue: Double               = 1.0
  var portfolioQty: Long               = 0L
  val ulSpot: Option[Double]           = ulProjectedPrice

  case class DW(
      uniqueId: String,
      projectedPrice: Option[Double] = None,
      delta: Option[Double] = None,
      putCall: Option[PutCall] = None,
      sellStatusesDefault: Seq[ScenarioStatus] = Seq.empty,
      buyStatusesDefault: Seq[ScenarioStatus] = Seq.empty,
      sellStatusesDynamic: Seq[ScenarioStatus] = Seq.empty,
      buyStatusesDynamic: Seq[ScenarioStatus] = Seq.empty
  )

  def initAlgo[F[_]: Applicative: Monad](ulId: String, portfolioQty: Long): F[Algo[F]] =
    Algo(
      ulId,
      lotSize,
      portfolioQty,
      sendOrder = sendOrderAction,
      logAlert = log.warn,
      logInfo = log.info,
      logError = log.error
    )

  def predictResidual(
      buyStatusesDefault: Seq[ScenarioStatus],
      sellStatusesDefault: Seq[ScenarioStatus],
      buyStatusesDynamic: Seq[ScenarioStatus],
      sellStatusesDynamic: Seq[ScenarioStatus],
      dwMarketProjectedPrice: Double,
      signedDelta: Double
  ): Long = {
    val bdOwnBestBidDefault = BigDecimal(
      buyStatusesDefault.sortWith(_.priceOnMarket < _.priceOnMarket).lastOption.map(_.priceOnMarket).getOrElse(0.0)
    ).setScale(2, RoundingMode.HALF_EVEN)
    val bdOwnBestAskDefault = BigDecimal(
      sellStatusesDefault
        .sortWith(_.priceOnMarket < _.priceOnMarket)
        .headOption
        .map(_.priceOnMarket)
        .getOrElse(Int.MaxValue.toDouble)
    ).setScale(2, RoundingMode.HALF_EVEN)
    val bdOwnBestBidDynamic = BigDecimal(
      buyStatusesDynamic.sortWith(_.priceOnMarket < _.priceOnMarket).lastOption.map(_.priceOnMarket).getOrElse(0.0)
    ).setScale(2, RoundingMode.HALF_EVEN)
    val bdOwnBestAskDynamic = BigDecimal(
      sellStatusesDynamic
        .sortWith(_.priceOnMarket < _.priceOnMarket)
        .headOption
        .map(_.priceOnMarket)
        .getOrElse(Int.MaxValue.toDouble)
    ).setScale(2, RoundingMode.HALF_EVEN)

    val qty: Long = {
      val (bdOwnBestBid, buyStatusList) = if (bdOwnBestBidDefault <= bdOwnBestBidDynamic) {
        (bdOwnBestBidDynamic, buyStatusesDynamic)
      } else {
        (bdOwnBestBidDefault, buyStatusesDefault)
      }
      val (bdOwnAskBid, sellStatusList) = if (bdOwnBestAskDefault <= bdOwnBestAskDynamic) {
        (bdOwnBestAskDefault, sellStatusesDefault)
      } else {
        (bdOwnBestAskDynamic, sellStatusesDynamic)
      }
      val sumMktVolBid = buyStatusList
        .filter(p => {
          p.priceOnMarket > bdOwnBestBid || p.priceOnMarket == 0.0
        })
        .map(_.qtyOnMarketL)
        .sum
      val sumMktVolAsk = sellStatusList
        .filter(p => {
          p.priceOnMarket < bdOwnAskBid || p.priceOnMarket == 0.0
        })
        .map(_.qtyOnMarketL)
        .sum
      sumMktVolBid - sumMktVolAsk
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
    Either.cond(order.getQuantityL >= 0, (), Error.StateError("Agent e. Pre-process order qty cannot be negative"))

  def sendOrderAction(act: OrderAction): Order = {
    log.info(s"Agent 1000. Send Order $act")
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
  }

  def preProcess[F[_]: Monad]: EitherT[F, Error, Order] =
    for {
      _ <- EitherT.fromEither(
        Either.cond(ulProjectedPrice.isDefined, (), Error.MarketError("Agent e. Underlying price is empty"))
      )
      dwList <- EitherT.rightT(dwMap.values.filter(p => {
        p.projectedPrice.isDefined && p.putCall.isDefined
      }))
      _ <- EitherT.rightT(log.info(s"Agent 1. Dw List: $dwList"))
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
      _ <- EitherT.rightT(log.info(s"Agent 2. Dw List: $dwSignDeltaList"))
      predictionResidual <- EitherT.rightT[F, guardian.Error](
        dwSignDeltaList
          .map(p =>
            predictResidual(
              buyStatusesDefault = p.buyStatusesDefault,
              sellStatusesDefault = p.sellStatusesDefault,
              buyStatusesDynamic = p.buyStatusesDynamic,
              sellStatusesDynamic = p.sellStatusesDynamic,
              dwMarketProjectedPrice = p.projectedPrice.get,
              signedDelta = p.delta.get
            )
          )
          .sum
      )
      _ <- EitherT.rightT(log.info(s"Agent 3. Prediction residual: $predictionResidual"))
      reversedAbsoluteResidual = absoluteResidual.toLong * -1
      _ <- EitherT.rightT(log.info(s"Agent 4. Absolute residual: $reversedAbsoluteResidual"))
      totalResidual = predictionResidual + reversedAbsoluteResidual
      _ <- EitherT.rightT(log.info(s"Agent 5. Total residual: $totalResidual"))
      direction <- EitherT.fromEither(
        if (totalResidual == 0) {
          Left(Error.MarketError("Agent e. Direction cannot be determined because total residual is zero"))
        } else if (totalResidual < 0) {
          Right(Direction.SELL)
        } else {
          Right(Direction.BUY)
        }
      )
      _ <- EitherT.rightT(log.info(s"Agent 6. Direction: $direction"))
      hzDirection = if (direction == Direction.SELL) BuySell.SELL else BuySell.BUY
      ulShiftedProjectedPrice <- EitherT.rightT[F, Error](shiftUlProjectedPrice(ulProjectedPrice.get, direction))
      _                       <- EitherT.rightT(log.info(s"Agent 7. Shifted price: $ulShiftedProjectedPrice from $ulProjectedPrice"))
      absTotalResidual = Math.abs(totalResidual)
      _        <- EitherT.rightT(log.info(s"Agent 8. Total residual: $absTotalResidual"))
      customId <- EitherT.rightT(CustomId.generate)
      order = Algo.createOrder(absTotalResidual, ulShiftedProjectedPrice.toDouble, hzDirection, customId)
      _ <- EitherT.fromEither(validatePositiveAmount(order))
      _ <- EitherT.rightT(log.info(s"Agent 9. CustomId: $customId, Total residual order: $order"))
    } yield order

  onOrder {
    case Nak(t) =>
      val hzOrder = t.getOrderCopy
      algo.map(_.handleOnOrderNak(CustomId.fromOrder(hzOrder), "Agent e. Nak signal / order rejected", preProcess))

    case Ack(t) =>
      val hzOrder = t.getOrderCopy
      log.info(s"Agent 2000. Got ack : $t")
      algo.map(_.handleOnOrderAck(hzOrder, CustomId.fromOrder(hzOrder), preProcess))

    case TrxMessages.Rejected(t) =>
      val hzOrder = t.getOrderCopy
      algo.map(
        _.handleOnOrderNak(
          CustomId.fromOrder(hzOrder),
          "Agent e. Nak signal / order rejected (TrxMessages.Rejected)",
          preProcess
        )
      )
  }

  onMessage {

    case Load =>
      log.info("Agent Loading")
      val ulId     = ulInstrument.getUniqueId
      val sSummary = source[Summary]
      pointValue = Option(hedgeInstrument.getPointValue).map(_.doubleValue()).getOrElse(1.0)
      algo.map(_.handleOnSignal(preProcess))

      // UL Projected Price
      source[Summary]
        .get(ulInstrument)
        .filter(s => {
          s.theoOpenPrice.isDefined
        })
        .onUpdate(s => {
          ulProjectedPrice = s.theoOpenPrice
          log.info(s"Agent. ulProjectedPrice: TheoOpenPrice price $ulProjectedPrice")
          algo.map(_.handleOnSignal(preProcess))
        })
      source[Summary]
        .get(ulInstrument)
        .filter(s => s.last.isDefined)
        .onUpdate(s => {
          ulProjectedPrice = if (ulProjectedPrice.isEmpty) s.last else ulProjectedPrice
          log.info(s"Agent. ulProjectedPrice: Last price $ulProjectedPrice")
          algo.map(_.handleOnSignal(preProcess))
        })
      source[Summary]
        .get(ulInstrument)
        .filter(s => s.closePrevious.isDefined && s.last.isEmpty)
        .onUpdate(s => {
          log.info(s"Agent. ulProjectedPrice: Close previous $ulProjectedPrice")
          ulProjectedPrice = if (ulProjectedPrice.isEmpty) s.closePrevious else ulProjectedPrice
          algo.map(_.handleOnSignal(preProcess))
        })
      // Portfolio qty
      source[RiskPositionDetailsContainer]
        .get(hedgePortfolio, ulInstrument.getUniqueId, true)
        .filter(p =>
          Option(p).isDefined && Option(p.getTotalPosition).isDefined && Option(p.getTotalPosition.getNetQty).isDefined
        )
        .onUpdate(p => {
          portfolioQty = p.getTotalPosition.getNetQty.toLong
          log.info(s"Agent. portfolioQty: $portfolioQty")
          algo = if (algo.isEmpty) Some(initAlgo[Id](ulId, portfolioQty)) else algo
          algo.map(_.handleOnSignal(preProcess))
        })
      // Absolute Residual
      source[RiskPositionByULContainer]
        .get(portfolioId, ulInstrument.getUniqueId, true)
        .onUpdate(p => {
          absoluteResidual =
            if (p.getTotalPosition.getUlSpot == 0.0 || pointValue == 0.0) BigDecimal("0")
            else {
              log.info(s"Agent. getDeltaCashUlCurr: ${p.getTotalPosition.getDeltaCashUlCurr}")
              log.info(s"Agent. getUlSpot: ${p.getTotalPosition.getUlSpot}")
              BigDecimal(p.getTotalPosition.getDeltaCashUlCurr / p.getTotalPosition.getUlSpot / pointValue)
            }
          log.info(s"Agent. absoluteResidual: $absoluteResidual")
          algo.map(_.handleOnSignal(preProcess))
        })
      // DW
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
          log.info(s"Agent. Subscribing to Executions on [$d]")
          sSummary
            .get(dwInstrument)
            .filter(s => s.theoOpenPrice.isDefined)
            .onUpdate(s => {
              val x = dwMap
                .getOrElse(dwInstrument.getUniqueId, DW(dwInstrument.getUniqueId))
                .copy(projectedPrice = s.theoOpenPrice)
              dwMap += (x.uniqueId -> x)
              algo.map(_.handleOnSignal(preProcess))
              log.info(s"Agent. DW price: ${dwInstrument.getUniqueId}, price: ${s.theoOpenPrice}")
            })

          sSummary
            .get(dwInstrument)
            .filter(s => s.theoOpenVolume.isDefined)
            .onUpdate(s => {
              algo.map(_.handleOnSignal(preProcess))
              log.info(s"Agent. DW volume: ${dwInstrument.getUniqueId}, volume: ${s.theoOpenVolume}")
            })

          // Delta
          source[Pricing]
            .get(dwInstrument.getUniqueId, "DEFAULT")
            .filter(s => Option(s.delta).isDefined)
            .onUpdate(s => {
              val x =
                dwMap.getOrElse(dwInstrument.getUniqueId, DW(dwInstrument.getUniqueId)).copy(delta = Some(s.delta))
              dwMap += (x.uniqueId -> x)
              log.info(s"Agent. DW delta: ${dwInstrument.getUniqueId}, delta: ${s.delta}")
              algo.map(_.handleOnSignal(preProcess))
            })
          // Default
          source[AutomatonStatus]
            .get(exchange, "DEFAULT", dwInstrument.getUniqueId, "REFERENCE")
            .onUpdate(s => {
              val x = dwMap
                .getOrElse(dwInstrument.getUniqueId, DW(dwInstrument.getUniqueId))
                .copy(buyStatusesDefault = if (s.buyStatuses(0).scenarioStatus == 65535) s.buyStatuses else List.empty)
              dwMap += (x.uniqueId -> x)
              algo.map(_.handleOnSignal(preProcess))
            })
          source[AutomatonStatus]
            .get(exchange, "DEFAULT", dwInstrument.getUniqueId, "REFERENCE")
            .onUpdate(s => {
              val x = dwMap
                .getOrElse(dwInstrument.getUniqueId, DW(dwInstrument.getUniqueId))
                .copy(sellStatusesDefault =
                  if (s.sellStatuses(0).scenarioStatus == 65535) s.sellStatuses else List.empty
                )
              dwMap += (x.uniqueId -> x)
              algo.map(_.handleOnSignal(preProcess))
            })
          // Dynamic
          source[AutomatonStatus]
            .get(exchange, "DYNAMIC", dwInstrument.getUniqueId, "REFERENCE")
            .onUpdate(s => {
              val x = dwMap
                .getOrElse(dwInstrument.getUniqueId, DW(dwInstrument.getUniqueId))
                .copy(buyStatusesDynamic = if (s.buyStatuses(0).scenarioStatus == 65535) s.buyStatuses else List.empty)
              dwMap += (x.uniqueId -> x)
              algo.map(_.handleOnSignal(preProcess))
            })
          source[AutomatonStatus]
            .get(exchange, "DYNAMIC", dwInstrument.getUniqueId, "REFERENCE")
            .onUpdate(s => {
              val x = dwMap
                .getOrElse(dwInstrument.getUniqueId, DW(dwInstrument.getUniqueId))
                .copy(sellStatusesDynamic =
                  if (s.sellStatuses(0).scenarioStatus == 65535) s.sellStatuses else List.empty
                )
              dwMap += (x.uniqueId -> x)
              algo.map(_.handleOnSignal(preProcess))
            })
        })

      sSummary.get(ulInstrument).map(_.modeStr.get) onUpdate {
        case "Startup" =>
          algo = None

        case "Pre-Open1" =>
        case "Open1"     =>
        case "Intermission" =>
          algo = None

        case "Pre-Open2" =>
        case "Open2"     =>
        case "Pre-close" =>
        case "OffHour" =>
          algo = None

        case "Closed" =>
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
