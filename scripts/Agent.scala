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
  var ulId: String = null
  val context      = "DEFAULT"
  val lotSize: Int = 100
  val exchange     = "SET-EMAPI-HMM-PROXY"

  var sSummary: SummarySourceProvider                  = null
  var sPricing: PricingSourceProvider                  = null
  var sRiskPosition: RiskPositionDetailsSourceProvider = null
  var sAutomaton: AutomatonStatusSourceProvider        = null
  var aSummary: Source[Summary]                        = null
  var algo: Option[Algo[Id]]                           = None

  var bestPrice: Option[Double] = None
  def getDwList(
      ds: IDictionaryProvider,
      ulId: String,
      exchangeName: String = exchange
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

  def getProjectedPrice(source: Source[Summary], inDe: InstrumentDescriptor): Either[Error, Double] =
    source.latest match {
      case Some(value) if value.theoOpenPrice.isDefined => Right(value.theoOpenPrice.get)
      case None                                         => Left(Error.MarketError(s"Projected price for ${inDe.getUniqueId} is empty"))
    }

  def getOwnBestBidPrice(source: SummarySourceProvider, inDe: InstrumentDescriptor): Either[Error, Double] =
    source.get(inDe).latest match {
      case Some(value) if value.buyPrice.isDefined => Right(value.buyPrice.get)
      case None                                    => Left(Error.MarketError(s"Own best bid price for ${inDe.getUniqueId} is empty"))
    }

  def getOwnBestAskPrice(source: SummarySourceProvider, inDe: InstrumentDescriptor): Either[Error, Double] =
    source.get(inDe).latest match {
      case Some(value) if value.sellPrice.isDefined => Right(value.sellPrice.get)
      case None                                     => Left(Error.MarketError(s"Own best ask price for ${inDe.getUniqueId} is empty"))
    }

  def getPortfolioQty(source: RiskPositionDetailsSourceProvider, ulId: String): Either[Error, Double] =
    source.get(portfolioId, ulId, true).latest match {
      case Some(value)
          if Option(value.getTotalPosition).isDefined && Option(value.getTotalPosition.getNetQty).isDefined =>
        Right(value.getTotalPosition.getNetQty)
      case None => Left(Error.StateError(s"Portfolio quantity for $ulId is empty"))
    }

  def calcUlQtyPreResidual(
      source: AutomatonStatusSourceProvider,
      ownBestBid: Double,
      ownBestAsk: Double,
      marketProjectedPrice: Double,
      signedDelta: Double,
      dwId: String,
      context: String = "DEFAULT",
      exchangeName: String = exchange
  ): Long = {
    val autoSource       = source.get(exchangeName, context, dwId, "REFERENCE")
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

  def getDelta(source: PricingSourceProvider, dwId: String, context: String = context): Either[Error, Double] =
    source.get(dwId, context).latest match {
      case Some(value) if Option(value.delta).isDefined =>
        Right(value.delta) // negative = put //Nop will confirm DEFAULT or DYNAMIC or both
      case None => Left(Error.MarketError(s"Delta for $dwId is empty"))
    }

  def getPointValue(hedgeInDe: InstrumentDescriptor): Either[Error, Double] =
    Option(hedgeInDe.getPointValue) match {
      case Some(value) => Right(value.doubleValue())
      case None        => Left(Error.MarketError(s"Point value for hedge ${hedgeInDe.getUniqueId} is empty"))
    }

  def getAbsoluteResidual(source: RiskPositionDetailsSourceProvider, pointValue: Double, ulId: String): BigDecimal =
    source.get(portfolioId, ulId, true).latest match {
      case Some(p)
          if Option(p.getTotalPosition).isDefined && Option(p.getTotalPosition.getDeltaCashUlCurr).isDefined && Option(
            p.getTotalPosition.getUlSpot
          ).isDefined =>
        BigDecimal(p.getTotalPosition.getDeltaCashUlCurr / p.getTotalPosition.getUlSpot / pointValue)
      case None => BigDecimal("0")
    }

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
      x <- EitherT.fromEither(getProjectedPrice(aSummary, ulInstrument))
      _ = log.info(s"0 $x")

      dwList <- EitherT.rightT[F, guardian.Error](getDwList(dictionaryService, ulId))
      _ = log.info(s"1. $dwList")
      eDwProjectedPriceList <- EitherT.rightT[F, guardian.Error](dwList.map(p => getProjectedPrice(aSummary, p)))
      _ = log.info(s"2. $eDwProjectedPriceList")
      eBestBidPriceList <- EitherT.rightT[F, guardian.Error](dwList.map(p => getOwnBestBidPrice(sSummary, p)))
      _ = log.warn(s"3. $eBestBidPriceList")
      eBestAskPriceList <- EitherT.rightT[F, guardian.Error](dwList.map(p => getOwnBestAskPrice(sSummary, p)))
      _ = log.warn(s"4. $eBestAskPriceList")
      eDeltaList <- EitherT.rightT[F, guardian.Error](dwList.map(p => getDelta(sPricing, p.getUniqueId)))
      _ = log.warn(s"5. $eDeltaList")
      eDwPutCallList <- EitherT.rightT[F, guardian.Error](dwList.map(getPutOrCall))
      _ = log.warn(s"6. $eDwPutCallList")
      ePointValue <- EitherT.fromEither(getPointValue(hedgeInstrument))
      _ = log.warn(s"7. $ePointValue")

      groupedInputs =
        (dwList, eDwProjectedPriceList, eBestBidPriceList).zipped.toList
          .zip(eBestAskPriceList)
          .map {
            case ((a, b, c), d) => (a, b, c, d)
          }
          .zip(eDeltaList)
          .map {
            case ((a, b, c, d), e) => (a, b, c, d, e)
          }
          .zip(eDwPutCallList)
          .map {
            case ((a, b, c, d, e), f) => (a, b, c, d, e, f)
          }
          .groupBy(p => p._2.isRight && p._3.isRight && p._4.isRight && p._5.isRight && p._6.isRight)

      badInputs = groupedInputs(false)
      _ <- EitherT.rightT[F, Error](
        badInputs
          .flatMap(p => p.productIterator.drop(1).map(_.asInstanceOf[Either[Error, Any]]).filter(_.isLeft))
          .foreach(log.warn)
      )
      _ <- EitherT.fromEither(
        Either.cond(groupedInputs.contains(true), (), Error.MarketError("No valid inputs to process"))
      )
      dwProjectedPriceList = groupedInputs(true).map(_._2.toOption.get)
      bestBidPriceList     = groupedInputs(true).map(_._3.toOption.get)
      bestAskPriceList     = groupedInputs(true).map(_._4.toOption.get)
      deltaList            = groupedInputs(true).map(_._5.toOption.get)
      dwPutCallList        = groupedInputs(true).map(_._6.toOption.get)
      absoluteResidual <- EitherT.rightT[F, guardian.Error](getAbsoluteResidual(sRiskPosition, ePointValue, ulId))
      _ = log.warn(s"8. $absoluteResidual")
      signedDeltaList <- EitherT.rightT[F, guardian.Error](
        (deltaList, dwPutCallList).zipped.toList
          .map {
            case (delta, CALL) => 1 * delta
            case (delta, PUT)  => -1 * delta
            case _             => 0
          }
      )
      _ = log.warn(s"9. $signedDeltaList")
      partialResidual <- EitherT.rightT[F, guardian.Error](
        (bestBidPriceList, bestAskPriceList, dwProjectedPriceList).zipped.toList
          .zip(signedDeltaList)
          .map {
            case ((a, b, c), d) => (a, b, c, d)
          }
          .zip(dwList)
          .map {
            case ((a, b, c, d), e) => (a, b, c, d, e.getUniqueId)
          }
          .map(p => calcUlQtyPreResidual(sAutomaton, p._1, p._2, p._3, p._4, p._5))
          .sum
      )
      _             = log.warn(s"10. $partialResidual")
      totalResidual = partialResidual + absoluteResidual.toLong
      _             = log.warn(s"11. $totalResidual")
      direction     = if (totalResidual < 0) Direction.SELL else Direction.BUY
      _             = log.warn(s"12. $direction")
      hzDirection   = if (direction == Direction.SELL) BuySell.SELL else BuySell.BUY
      _             = log.warn(s"13. $hzDirection")
      ulProjectedPrice <- EitherT.fromEither(getProjectedPrice(aSummary, ulInstrument))
      _ = log.warn(s"14 $ulProjectedPrice")
      ulShiftedProjectedPrice <- EitherT.rightT[F, Error](shiftUlProjectedPrice(ulProjectedPrice, direction))
      _                = log.warn(s"15. $ulShiftedProjectedPrice")
      absTotalResidual = Math.abs(totalResidual)
      _                = log.warn(s"16. $absTotalResidual")
      order            = Algo.createOrder(absTotalResidual, ulShiftedProjectedPrice.toDouble, hzDirection, CustomId.generate)
      _                = log.warn(s"17. $order")
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

  var ulProjectedPrice: Option[Double]

  var dwMap: Map[String, DW]         = Map.empty
  var absoluteResidual: Option[Long] = None

  case class DW(
      uniqueId: String,
      projectedPrice: Option[Double] = None,
      bestBidPrice: Option[Double] = None,
      bestAskPrice: Option[Double] = None,
      delta: Option[Double] = None,
      putCall: Option[PutCall] = None
  )

  def trigger = ()

  onMessage {

    case Load =>
      log.info("Agent Loading")
      ulId = ulInstrument.getUniqueId
      sSummary = source[Summary]
      sPricing = source[Pricing]
      sRiskPosition = source[RiskPositionDetailsContainer]
      sAutomaton = source[AutomatonStatus]
      aSummary = sSummary.get(ulInstrument)
//      algo = Some(initAlgo[Id](getPortfolioQty(sRiskPosition, ulId).getOrElse(0.0).toLong))
//      algo.map(_.handleOnSignal())

      sSummary
        .get(ulInstrument)
        .filter(s => s.theoOpenPrice.isDefined)
        .onUpdate(s => {
          algo.map(_.handleOnSignal())
          log.info(s"ProjPx= " + s.theoOpenPrice.get)
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
              trigger
              log.info(s"DW ProjPx= ${dwInstrument.getUniqueId} ${s.theoOpenPrice}")
            })
          sSummary
            .get(dwInstrument)
            .filter(s => s.buyPrice.isDefined)
            .onUpdate(s => {
              val x =
                dwMap.getOrElse(dwInstrument.getUniqueId, DW(dwInstrument.getUniqueId)).copy(bestBidPrice = s.buyPrice)
              dwMap += (x.uniqueId -> x)
              trigger
              log.info(s"DW ProjPx= ${dwInstrument.getUniqueId} ${s.buyPrice}")
            })
          sSummary
            .get(dwInstrument)
            .filter(s => s.sellPrice.isDefined)
            .onUpdate(s => {
              val x =
                dwMap.getOrElse(dwInstrument.getUniqueId, DW(dwInstrument.getUniqueId)).copy(bestAskPrice = s.sellPrice)
              dwMap += (x.uniqueId -> x)
              trigger
              log.info(s"DW ProjPx= ${dwInstrument.getUniqueId} ${s.sellPrice}")
            })
          sPricing
            .get(dwInstrument.getUniqueId, "DEFAULT")
            .filter(s => Option(s.delta).isDefined)
            .onUpdate(s => {
              val x =
                dwMap.getOrElse(dwInstrument.getUniqueId, DW(dwInstrument.getUniqueId)).copy(delta = Some(s.delta))
              dwMap += (x.uniqueId -> x)
              trigger
            })
        })
//      source[RiskPositionDetailsContainer].get(portfolioId, ulId, true)
//        .onUpdate(p => BigDecimal(p.getTotalPosition.getDeltaCashUlCurr / p.getTotalPosition.getUlSpot / pointValue)

      sRiskPosition
        .get(portfolioId, ulInstrument.getUniqueId, true)
        .onUpdate(p => {
          algo.map(_.handleOnSignal())
          log.info(p.getTotalPosition)
        })

      sSummary.get(ulInstrument).map(_.modeStr.get) onUpdate {
        case "Startup" =>
          algo = None

        case "Pre-Open1" =>
          algo = Some(initAlgo[Id](getPortfolioQty(sRiskPosition, ulId).getOrElse(0.0).toLong))

        case "Open1" =>
        case "Intermission" =>
          algo = None

        case "Pre-Open2" =>
          algo = Some(initAlgo[Id](getPortfolioQty(sRiskPosition, ulId).getOrElse(0.0).toLong))

        case "Open2" =>
        case "Pre-close" =>
          algo = Some(initAlgo[Id](getPortfolioQty(sRiskPosition, ulId).getOrElse(0.0).toLong))

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
