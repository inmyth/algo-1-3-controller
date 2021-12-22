package simple4

import algotrader.api.NativeController
import algotrader.api.Messages._
import algotrader.api.source.summary.SummarySourceBuilder
import com.hsoft.hmm.api.source.hedgerstatus.HedgerStatus
import com.hsoft.hmm.api.source.position.{RiskPositionByUlSourceBuilder, RiskPositionDetailsSourceBuilder}
import com.hsoft.hmm.api.source.pricing.{Pricing, PricingSourceBuilder}
import com.hsoft.hmm.posman.api.position.container.{RiskPositionByULContainer, RiskPositionDetailsContainer}
import com.hsoft.util.server.process.monitor.bus.ProcessMessages
import com.ingalys.imc.summary.Summary
import horizontrader.services.instruments.{InstrumentDescriptor, InstrumentInfoService}

import scala.collection.JavaConverters._

// case class MyMessage(myParam : Int)

trait controller extends NativeController {
  case class TriggerCal()

  val DWSymbol: String
  val DWInstrument: InstrumentDescriptor =
    getService[InstrumentInfoService].getInstrumentByUniqueId("SET-EMAPI-HMM-PROXY", DWSymbol)
  var DWMktStatus: String = null

  val ULSymbol: String
  val ULInstrument: InstrumentDescriptor =
    getService[InstrumentInfoService].getInstrumentByUniqueId("SET-EMAPI-HMM-PROXY", ULSymbol)
  var ULMktStatus: String = null

  val portfolioId = "JV"

  var DWDelta: Double = 0
  var DWQty: Double   = 0

  var ULDelta: Double = 1
  var ULQty: Double   = 0

  var Residule: Double = 0

  var TotalDeltaCash: Double = 0
  var ULSpot: Double         = 0
  var Residule2: Double      = 0
  var HedgerResidule: Double = 0

  var ULBidPx: Double  = 0
  var ULAskPx: Double  = 0
  var ULLastPx: Double = 0
  var ULProjPx: Double = 0

  var DWBidPx: Double  = 0
  var DWAskPx: Double  = 0
  var DWLastPx: Double = 0
  var DWProjPx: Double = 0

  var HedgeDelta: Double = 0

  onMessage {
    // react on message here
    // case MyMessage(i) =>

    case Load  =>
    case Start =>
      //Update Position*price
      val PricingSource = source[Pricing].get(DWSymbol, "DEFAULT")
      PricingSource
        .filter(p => p.getDelta != 0)
        .onUpdate(p => {
          DWDelta = p.getDelta
          //        log.info(s"ProductID=" + p.getProductId + "Delta=" + p.getDelta)
          processMessage(TriggerCal)
        })

      val PositionByULSource = source[RiskPositionByULContainer]
      PositionByULSource
        .get(portfolioId, ULSymbol, true)
        .onUpdate(p => {
          ULSpot = p.getTotalPosition.getUlSpot
          TotalDeltaCash = p.getTotalPosition.getDeltaCashUlCurr
          //        log.info (s"Total Delta Cash="+TotalDeltaCash+" ULSpot="+ULSpot)
          processMessage(TriggerCal)
        })

      val PositionSource = source[RiskPositionDetailsContainer].get(portfolioId, DWSymbol, true)
      PositionSource.onUpdate(p => {
        DWQty = p.getTotalPosition.getNetQty
        HedgeDelta = p.getTotalPosition.getDelta
        //        log.info(s"TotalPosition of " + p.getProductId + " TotalPosition=" + p.getTotalPosition.getNetQty)
        processMessage(TriggerCal)
      })

      val PositionSource2 = source[RiskPositionDetailsContainer].get(portfolioId, ULSymbol, true)
      PositionSource2.onUpdate(p => {
        ULQty = p.getTotalPosition.getNetQty
        //        log.info(s"TotalPosition of " + p.getProductId + " TotalPosition=" + p.getTotalPosition.getNetQty)
        processMessage(TriggerCal)
      })

      //summarySource.map(_.modeStr.get).onUpdate(status => {})
      val summarySourceUL = source[Summary].get(ULInstrument)
      summarySourceUL
        .filter(s => s.modeStr.isDefined)
        .onUpdate(s => {
          ULMktStatus = s.modeStr.get
          processMessage(TriggerCal)
        })

      summarySourceUL
        .filter(s => s.buyPrice.isDefined)
        .onUpdate(s => {
          ULBidPx = s.buyPrice.get
          processMessage(TriggerCal)
        })

      summarySourceUL
        .filter(s => s.sellPrice.isDefined)
        .onUpdate(s => {
          ULAskPx = s.sellPrice.get
          processMessage(TriggerCal)
        })

      summarySourceUL
        .filter(s => s.last.isDefined)
        .onUpdate(s => {
          ULLastPx = s.last.get
          processMessage(TriggerCal)
        })

      summarySourceUL
        .filter(s => s.theoOpenPrice.isDefined)
        .onUpdate(s => {
          ULProjPx = s.theoOpenPrice.get
          processMessage(TriggerCal)
        })

      val summarySourceDW = source[Summary].get(DWInstrument)
      summarySourceDW
        .filter(s => s.modeStr.isDefined)
        .onUpdate(s => {
          DWMktStatus = s.modeStr.get
          processMessage(TriggerCal)
        })

      summarySourceDW
        .filter(s => s.buyPrice.isDefined)
        .onUpdate(s => {
          DWBidPx = s.buyPrice.get
          processMessage(TriggerCal)
        })

      summarySourceDW
        .filter(s => s.sellPrice.isDefined)
        .onUpdate(s => {
          DWAskPx = s.sellPrice.get
          processMessage(TriggerCal)
        })

      summarySourceDW
        .filter(s => s.last.isDefined)
        .onUpdate(s => {
          DWLastPx = s.last.get
          processMessage(TriggerCal)
        })

      summarySourceDW
        .filter(s => s.theoOpenPrice.isDefined)
        .onUpdate(s => {
          DWProjPx = s.theoOpenPrice.get
          processMessage(TriggerCal)
        })

      val HedgerSource = source[HedgerStatus]
      HedgerSource
        .get(portfolioId, ULSymbol)
        .onUpdate(h => {
          HedgerResidule = h.qtyResidual
          log.info(s"UL=" + ULSymbol + " HedgerRes=" + HedgerResidule)
          processMessage(TriggerCal)
        })

    //Update Delta_cash/ULspot

    case TriggerCal => {
      //      Absolute Residule=((HedgeDelta*DWQty) + (ULDelta*ULQty)) * -1
      //      Prediction Residule = Prediction QtyDW * if HedgeDelta!=0 HedgeDelta , DeltaPricing
      Residule = ((HedgeDelta * DWQty) + (ULQty)) * -1
      Residule2 = (TotalDeltaCash / ULSpot) * -1

      log.info(
        s"Cal UL=" + ULSymbol + " DW=" + DWSymbol + " Res=" + Residule + " Res2=" + Residule2 + " HedgerRes=" + HedgerResidule + " ULStatus=" + ULMktStatus + " DWStatus=" + DWMktStatus + " HedgeDelta=" + HedgeDelta + " DWDelta=" + DWDelta + " DWQty=" + DWQty + " ULDelta=" + ULDelta + " ULQty=" + ULQty + " DeltaCash=" + TotalDeltaCash + " ULSpot=" + ULSpot
          + " ULBid=" + ULBidPx + " ULAsk=" + ULAskPx + " ULLast=" + ULLastPx + " ULProj=" + ULProjPx + " DWBid=" + DWBidPx + " DWAsk=" + DWAskPx + " DWLast=" + DWLastPx + " DWProj=" + DWProjPx
      )

      //      log.info(s"Calculate#1 UL=" + ULSymbol +" ULMktStatus="+ULMktStatus+" DWMktStauts="+DWMktStatus+" DWDelta=" + DWDelta + " DWQty=" + DWQty+" ULDelta=" + ULDelta + " ULQty=" + ULQty+" Residule="+Residule+" HedgerResidule="+HedgerResidule
      //        +" ULBidPx="+ULBidPx+" ULAskPx="+ULAskPx+" ULLastPx="+ULLastPx+" ULProjPx="+ULProjPx+" DWBidPx="+DWBidPx+" DWAskPx="+DWAskPx+" DWLastPx="+DWLastPx+" DWProjPx="+DWProjPx)
      //
      //      log.info (s"Calculate#2 UL=" + ULSymbol +" ULMktStatus="+ULMktStatus+" DWMktStauts="+DWMktStatus+" TotalDeltaCash="+TotalDeltaCash+" ULSpot="+ULSpot+" Residule2="+Residule2+" HedgerResidule="+HedgerResidule
      //        +" ULBidPx="+ULBidPx+" ULAskPx="+ULAskPx+" ULLastPx="+ULLastPx+" ULProjPx="+ULProjPx+" DWBidPx="+DWBidPx+" DWAskPx="+DWAskPx+" DWLastPx="+DWLastPx+" DWProjPx="+DWProjPx)
    }

  }

}
