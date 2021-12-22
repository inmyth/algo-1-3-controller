package mrt

import horizontrader.services.instruments.InstrumentDescriptor
import algotrader.api.{NativeTradingAgent, TrxMessages}
import algotrader.api.Messages._
import algotrader.api.source.depth.DepthSourceBuilder
import algotrader.api.source.summary._
import com.ingalys.imc.BuySell
import cats.{Applicative, Id, Monad}
import cats.data.EitherT
import cats.implicits.{catsSyntaxApplicativeId, toTraverseOps}
import com.hsoft.datamaster.product.{Derivative, ProductTypes, Warrant}
import com.hsoft.hmm.api.source.automatonstatus.AutomatonStatus
import com.hsoft.hmm.api.source.position.{RiskPositionByUlSourceBuilder, RiskPositionDetailsSourceBuilder}
import com.hsoft.hmm.api.source.pricing.{Pricing, PricingSourceBuilder}
import com.hsoft.hmm.posman.api.position.container.{RiskPositionByULContainer, RiskPositionDetailsContainer}
import com.hsoft.scenario.status.ScenarioStatus
import com.ingalys.imc.depth.Depth
import com.ingalys.imc.order.Order
import com.ingalys.imc.summary.Summary
import guardian.Algo.MyScenarioStatus
import guardian.{Algo, Error}
import guardian.Entities.{CustomId, Direction, OrderAction, PutCall}
import guardian.Error.MarketError
import horizontrader.plugins.hmm.connections.service.IDictionaryProvider
import horizontrader.services.instruments.InstrumentInfoService

import scala.collection.JavaConverters._
import scala.language.higherKinds
import scala.math.BigDecimal.RoundingMode
import scala.util.{Failure, Success, Try}

trait Agent extends NativeTradingAgent {
  val portfolioId: String                    //JV
  val hedgePortfolio: String                 //9901150 Buy Sell UL
  val ulInstrument: InstrumentDescriptor     //RBF@XBKK
  val hedgeInstrument: InstrumentDescriptor  //RBF@XBKK
  val dictionaryService: IDictionaryProvider // List of RBF@XBKK DW
  val context                       = "DEFAULT"
  val lotSize: Int                  = 100
  val exchange                      = "SET-EMAPI-HMM-PROXY"
  var algo: Option[Algo[Id]]        = None
  var dwMap: Map[String, DW]        = Map.empty
  var absoluteResidual: BigDecimal  = BigDecimal("0")
  var pointValue: Double            = 1.0
  var portfolioQty: Long            = 0L
  var theoOpenPrice: Option[Double] = None
  var lastPrice: Option[Double]     = None
  var closePrevious: Option[Double] = None

  def toMyScenarioStatus(s: ScenarioStatus): Option[MyScenarioStatus] =
    Try {
      MyScenarioStatus(BigDecimal(s.priceOnMarket).setScale(2, RoundingMode.HALF_EVEN), s.qtyOnMarketL)
    }.toOption

  case class DW(
      uniqueId: String,
      projectedPrice: Option[Double] = None,
      projectedVol: Option[Long] = None,
      delta: Option[Double] = None,
      putCall: Option[PutCall] = None,
      marketSells: Seq[MyScenarioStatus] = Seq.empty,
      marketBuys: Seq[MyScenarioStatus] = Seq.empty,
      ownSellStatusesDefault: Seq[MyScenarioStatus] = Seq.empty,
      ownBuyStatusesDefault: Seq[MyScenarioStatus] = Seq.empty,
      ownSellStatusesDynamic: Seq[MyScenarioStatus] = Seq.empty,
      ownBuyStatusesDynamic: Seq[MyScenarioStatus] = Seq.empty
  )

  def initAlgo[F[_]: Applicative: Monad](ulId: String): Algo[F] =
    Algo(
      underlyingSymbol = ulId,
      lotSize = lotSize,
      logAlert = log.warn,
      logInfo = log.info,
      logError = log.error
    )

  def shiftUlProjectedPrice(price: Double, direction: Direction): BigDecimal =
    Algo.getPriceAfterTicks(if (direction == Direction.BUY) true else false, BigDecimal(price))

  def validatePositiveAmount(order: Order): Either[Error, Unit] =
    Either.cond(order.getQuantityL >= 0, (), Error.StateError("Agent e. Pre-process order qty cannot be negative"))

  def sendOrderAction(act: OrderAction): Unit =
    if (!Lock.getStopBot(ulInstrument.getUniqueId)) {
      act match {
        case OrderAction.InsertOrder(order, _) =>
          log.info(s"Agent 1000. Send Insert Order lock:${Lock.getStopBot(ulInstrument.getUniqueId)} $act")
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

        case OrderAction.UpdateOrder(activeOrderDescriptorView, order) =>
          log.info(
            s"Agent 1100. Send Update Order lock:${Lock
              .getStopBot(ulInstrument.getUniqueId)} new qty: ${order.getQuantityL} new price: ${order.getPrice} $order"
          )
          updateOrder(
            ulInstrument,
            getOrderByUserData(activeOrderDescriptorView.getUserData).get.getOrderCopy.deepClone().asInstanceOf[Order],
            (o, _) => {
              o.setPrice(order.getPrice)
              o.setQuantity(order.getQuantityL)
              o
            }
          )

        case OrderAction.CancelOrder(activeOrderDescriptorView, order) =>
          log.info(s"Agent 1200. Send Cancel Order lock:${Lock.getStopBot(ulInstrument.getUniqueId)} : $order")
          deleteOrder(activeOrderDescriptorView)
      }
    }

  def preProcess[F[_]: Monad]: EitherT[F, Error, Order] =
    for {
      ulProjectedPrice <- EitherT.fromEither[F](
        (theoOpenPrice, lastPrice, closePrevious) match {
          case (Some(v), _, _)       => Right(v)
          case (None, Some(v), _)    => Right(v)
          case (None, None, Some(v)) => Right(v)
          case _                     => Left(Error.MarketError("Agent e. Underlying price is empty"))
        }
      )
      dwList <- EitherT.rightT(dwMap.values.filter(p => {
//        log.info(
//          s"pre-predictionresidual projected price ${p.projectedPrice}, projected vol ${p.projectedVol}, market buys ${p.marketBuys}, market sells ${p.marketSells}, ownBuyStatusesDefault ${p.ownBuyStatusesDefault}, ownSellStatusesDefault ${p.ownBuyStatusesDefault}, ownBuyStatusesDynamic ${p.ownBuyStatusesDynamic}, ownSellStatusesDefault ${p.ownSellStatusesDynamic}"
//        )
        p.projectedPrice.isDefined && BigDecimal(p.projectedPrice.get)
          .setScale(2, RoundingMode.HALF_EVEN) != BigDecimal("0") &&
          p.projectedVol.isDefined && p.projectedVol.get != 0L && p.putCall.isDefined &&
          (p.marketBuys.nonEmpty || p.marketSells.nonEmpty) &&
          (p.ownBuyStatusesDefault.nonEmpty || p.ownSellStatusesDefault.nonEmpty || p.ownBuyStatusesDynamic.nonEmpty || p.ownSellStatusesDynamic.nonEmpty)
      }))
      _ <- EitherT.rightT(log.info(s"Agent 1. Dw List: $dwList"))
      predictionResidual <- EitherT.rightT[F, guardian.Error](
        dwList
          .filter(d => d.delta.isDefined)
          .map(p =>
            Algo.predictResidual(
              marketBuys = p.marketBuys,
              marketSells = p.marketSells,
              ownBuyStatusesDefault = p.ownBuyStatusesDefault,
              ownSellStatusesDefault = p.ownSellStatusesDefault,
              ownBuyStatusesDynamic = p.ownBuyStatusesDynamic,
              ownSellStatusesDynamic = p.ownSellStatusesDynamic,
              dwMarketProjectedPrice = p.projectedPrice.get,
              dwMarketProjectedQty = p.projectedVol.get,
              signedDelta = p.delta.get,
              log = log.info
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
        if (totalResidual < 0) {
          Right(Direction.SELL)
        } else {
          Right(Direction.BUY)
        }
      )
      hzDirection = if (direction == Direction.SELL) BuySell.SELL else BuySell.BUY
      ulShiftedProjectedPrice <- EitherT.rightT[F, Error](shiftUlProjectedPrice(ulProjectedPrice, direction))
      _                       <- EitherT.rightT(log.info(s"Agent 7. Shifted price: $ulShiftedProjectedPrice from $ulProjectedPrice"))
      absTotalResidual = Math.abs(totalResidual)
      customId <- EitherT.rightT(CustomId.generate)
      order = Algo.createPreProcessOrder(absTotalResidual, ulShiftedProjectedPrice.toDouble, hzDirection, customId)
      _ <- EitherT.fromEither(validatePositiveAmount(order))
      _ <- EitherT.rightT(log.info(s"Agent 9. CustomId: $customId, Total residual order: $order"))
    } yield order

  def processAndSend(c: EitherT[Id, Error, List[OrderAction]]): Unit =
    (for {
      oas <- c
      _ = log.info(s"Lock processandsend ${Lock.getStopBot(ulInstrument.getUniqueId)}")
      send <- EitherT.cond[Id](!Lock.getStopBot(ulInstrument.getUniqueId), oas, MarketError("Agent. Force stop bot"))
      _    <- EitherT.right[Error](send.map(p => sendOrderAction(p).pure[Id]).sequence)
    } yield ()).value match {
      case Right(_) => ()
      case Left(e) =>
        log.warn(e.msg)
    }

  onOrder {
    case Nak(activeOrderDescriptorView) =>
      val hzOrder = activeOrderDescriptorView.getOrderCopy
      algo.map(
        _.handleOnOrderNak(
          CustomId.fromOrder(hzOrder),
          s"Agent e. Nak signal / order rejected id: ${activeOrderDescriptorView.getOrderCopy.getId}, $activeOrderDescriptorView"
        )
      )

    case Ack(activeOrderDescriptorView) =>
      log.info(
        s"Agent 2000. Got ack id: ${activeOrderDescriptorView.getOrderCopy.getId}, status: ${activeOrderDescriptorView.getExecutionStatus}, $activeOrderDescriptorView"
      )
      val ots = algo
        .map(_.handleOnOrderAck(activeOrderDescriptorView, preProcess))
        .getOrElse(EitherT.rightT[Id, Error](List.empty[OrderAction]))
      processAndSend(ots)

    case TrxMessages.Rejected(t) =>
      val hzOrder = t.getOrderCopy
      algo.map(
        _.handleOnOrderNak(
          CustomId.fromOrder(hzOrder),
          s"Agent e. Nak signal / order rejected (TrxMessages.Rejected): $t"
        )
      )

    case Executed(_, activeOrderDescriptorView) =>
      log.info(
        s"Agent 2500. Got executed, stopping the bot. id: ${activeOrderDescriptorView.getOrderCopy.getId}, status: ${activeOrderDescriptorView.getExecutionStatus} $activeOrderDescriptorView"
      )
      algo = None
  }

  onMessage {

    case StartBot =>
      log.info("Agent StartBot")
      val ulId     = ulInstrument.getUniqueId
      val sSummary = source[Summary]
      pointValue = Option(hedgeInstrument.getPointValue).map(_.doubleValue()).getOrElse(1.0)
      algo = if (algo.isEmpty) Some(initAlgo[Id](ulId)) else algo

      // UL Projected Price
      source[Summary]
        .get(ulInstrument)
        .filter(s => {
          s.theoOpenPrice.isDefined
        })
        .onUpdate(s => {
          theoOpenPrice = s.theoOpenPrice
          log.info(s"Agent. ulProjectedPrice: TheoOpenPrice price $theoOpenPrice")
          val ots = EitherT(algo.map(_.handleOnSignal(preProcess)).getOrElse(Right(List.empty[OrderAction]).pure[Id]))
          processAndSend(ots)
          // price and qty, price changes then update all live orders with new price, if both change
        })
      source[Summary]
        .get(ulInstrument)
        .filter(s => s.last.isDefined)
        .onUpdate(s => {
          lastPrice = s.last
          log.info(s"Agent. ulProjectedPrice: Last price $lastPrice")
          val ots = EitherT(algo.map(_.handleOnSignal(preProcess)).getOrElse(Right(List.empty[OrderAction]).pure[Id]))
          processAndSend(ots)
        })
      source[Summary]
        .get(ulInstrument)
        .filter(s => s.closePrevious.isDefined)
        .onUpdate(s => {
          closePrevious = s.closePrevious
          log.info(s"Agent. ulProjectedPrice: Close previous $closePrevious")
          val ots = EitherT(algo.map(_.handleOnSignal(preProcess)).getOrElse(Right(List.empty[OrderAction]).pure[Id]))
          processAndSend(ots)
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
          algo.map(_.handleOnPortfolio(portfolioQty))
          val ots = EitherT(algo.map(_.handleOnSignal(preProcess)).getOrElse(Right(List.empty[OrderAction]).pure[Id]))
          processAndSend(ots)
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
          val ots = EitherT(algo.map(_.handleOnSignal(preProcess)).getOrElse(Right(List.empty[OrderAction]).pure[Id]))
          processAndSend(ots)
        })
      // DW
      dictionaryService.getDictionary
        .getProducts(null)
        .values()
        .asScala
        .filter(p => {
          p.getProductType == ProductTypes.WARRANT
        })
        .map(p => p.asInstanceOf[Derivative])
        .filter(d => d.getUlId == ulId)
        .foreach(d => {
          val dwInstrument = getService[InstrumentInfoService].getInstrumentByUniqueId(exchange, d.getId)
          val putCall = dictionaryService.getDictionary.getProduct(d.getId).asInstanceOf[Warrant].getOptionType match {
            case 1 => Some(PutCall.CALL)
            case 2 => Some(PutCall.PUT)
            case _ => None
          }
          dwMap += (dwInstrument.getUniqueId -> DW(
            uniqueId = dwInstrument.getUniqueId,
            putCall = putCall
          ))
          sSummary
            .get(dwInstrument)
            .onUpdate(s => {
              log.info(
                s"Agent. DW projected price & vol: ${dwInstrument.getUniqueId}, price: ${s.theoOpenPrice}, vol: ${s.theoOpenVolume}"
              )

              (s.theoOpenPrice, s.theoOpenVolume) match {
                case (Some(_), Some(_)) =>
                  val x = dwMap // Array, Set, Map(key, value)
                    .getOrElse(dwInstrument.getUniqueId, DW(dwInstrument.getUniqueId))
                    .copy(projectedPrice = s.theoOpenPrice, projectedVol = s.theoOpenVolume)
                  dwMap += (x.uniqueId -> x)
                  val ots =
                    EitherT(algo.map(_.handleOnSignal(preProcess)).getOrElse(Right(List.empty[OrderAction]).pure[Id]))
                  processAndSend(ots)

                case (None, None) =>
                  val x = dwMap
                    .getOrElse(dwInstrument.getUniqueId, DW(dwInstrument.getUniqueId))
                    .copy(projectedPrice = Some(0.0), projectedVol = Some(0L))
                  dwMap += (x.uniqueId -> x)
                  val ots =
                    EitherT(algo.map(_.handleOnSignal(preProcess)).getOrElse(Right(List.empty[OrderAction]).pure[Id]))
                  processAndSend(ots)

                case _ =>
              }
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
              val ots =
                EitherT(algo.map(_.handleOnSignal(preProcess)).getOrElse(Right(List.empty[OrderAction]).pure[Id]))
              processAndSend(ots)
            })

          // Market buys & sells
          val summaryDepth = source[Depth].get(dwInstrument)
          summaryDepth.onUpdate(d => {
            val buyCount = Option(d.getBuyCount).getOrElse(0)
            val buys = (0 until buyCount)
              .map(d.getBuy)
              .filter(_ != null)
              .map(p => MyScenarioStatus(p.getPrice, p.getQuantityL))
              .toVector
            val sellCount = Option(d.getSellCount).getOrElse(0)
            val sells = (0 until sellCount)
              .map(d.getSell)
              .filter(_ != null)
              .map(p => MyScenarioStatus(p.getPrice, p.getQuantityL))
              .toVector

//            if (
//              buys.filter(p => p.qtyOnMarketL != 0L && p.priceOnMarket != 0.0).isEmpty &&
//              sells.filter(p => p.qtyOnMarketL != 0L && p.priceOnMarket != 0.0).isEmpty
//            ) ()
//            else {
            val x = dwMap
              .getOrElse(dwInstrument.getUniqueId, DW(dwInstrument.getUniqueId))
              .copy(marketBuys = buys, marketSells = sells)
            dwMap += (x.uniqueId -> x)
            val ots =
              EitherT(algo.map(_.handleOnSignal(preProcess)).getOrElse(Right(List.empty[OrderAction]).pure[Id]))
            log.info(s"Agent. DW market buys and sells: ${dwInstrument.getUniqueId} $x")
            processAndSend(ots)
//            }
          })

          // Default Own order
          source[AutomatonStatus]
            .get(exchange, "DEFAULT", dwInstrument.getUniqueId, "REFERENCE")
            .onUpdate(s => {
              val x = dwMap
                .getOrElse(dwInstrument.getUniqueId, DW(dwInstrument.getUniqueId))
                .copy(ownBuyStatusesDefault =
                  if (s.buyStatuses(0).scenarioStatus == 65535)
                    s.buyStatuses.filter(_ != null).map(toMyScenarioStatus).toVector.flatten
                  else Vector.empty
                )
              dwMap += (x.uniqueId -> x)
              val ots =
                EitherT(algo.map(_.handleOnSignal(preProcess)).getOrElse(Right(List.empty[OrderAction]).pure[Id]))
              log.info(s"Agent. DW default own buys: ${dwInstrument.getUniqueId} $x")

              processAndSend(ots)
            })
          source[AutomatonStatus]
            .get(exchange, "DEFAULT", dwInstrument.getUniqueId, "REFERENCE")
            .onUpdate(s => {
              val x = dwMap
                .getOrElse(dwInstrument.getUniqueId, DW(dwInstrument.getUniqueId))
                .copy(ownSellStatusesDefault =
                  if (s.sellStatuses(0).scenarioStatus == 65535)
                    s.sellStatuses.filter(_ != null).map(toMyScenarioStatus).toVector.flatten
                  else Vector.empty
                )
              dwMap += (x.uniqueId -> x)
              val ots =
                EitherT(algo.map(_.handleOnSignal(preProcess)).getOrElse(Right(List.empty[OrderAction]).pure[Id]))
              log.info(s"Agent. DW default own sells: ${dwInstrument.getUniqueId} $x")

              processAndSend(ots)
            })
          // Dynamic Own order
          source[AutomatonStatus]
            .get(exchange, "DYNAMIC", dwInstrument.getUniqueId, "REFERENCE")
            .onUpdate(s => {
              val x = dwMap
                .getOrElse(dwInstrument.getUniqueId, DW(dwInstrument.getUniqueId))
                .copy(ownBuyStatusesDynamic =
                  if (s.buyStatuses(0).scenarioStatus == 65535)
                    s.buyStatuses.filter(_ != null).map(toMyScenarioStatus).toVector.flatten
                  else Vector.empty
                )
              dwMap += (x.uniqueId -> x)
              val ots =
                EitherT(algo.map(_.handleOnSignal(preProcess)).getOrElse(Right(List.empty[OrderAction]).pure[Id]))
              log.info(s"Agent. DW dynamic own buys: ${dwInstrument.getUniqueId} $x")

              processAndSend(ots)
            })
          source[AutomatonStatus]
            .get(exchange, "DYNAMIC", dwInstrument.getUniqueId, "REFERENCE")
            .onUpdate(s => {
              val x = dwMap
                .getOrElse(dwInstrument.getUniqueId, DW(dwInstrument.getUniqueId))
                .copy(ownSellStatusesDynamic =
                  if (s.sellStatuses(0).scenarioStatus == 65535)
                    s.sellStatuses.filter(_ != null).map(toMyScenarioStatus).toVector.flatten
                  else Vector.empty
                )
              dwMap += (x.uniqueId -> x)
              val ots =
                EitherT(algo.map(_.handleOnSignal(preProcess)).getOrElse(Right(List.empty[OrderAction]).pure[Id]))
              log.info(s"Agent. DW dynamic own sells: ${dwInstrument.getUniqueId} $x")

              processAndSend(ots)
            })
        })

    case Load  => log.info("Agent loading")
    case Start => log.info("Agent starting")

    case StopBot =>
      log.info("Agent. Got StopBot")
      algo = None
  }
}
