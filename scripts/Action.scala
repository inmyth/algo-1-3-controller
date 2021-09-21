import algotrader.api.NativeAction
import horizontrader.services.instruments.InstrumentDescriptor
import algotrader.api.NativeTradingAgent
import algotrader.api.Messages._
import com.ingalys.imc.BuySell
import horizontrader.services.instruments.InstrumentDescriptor

import scala.collection.JavaConverters._
import algotrader.api.source.Source
import com.hsoft.hmm.imsencaps.execution.HMMEncapsMarketDataField
import com.hsoft.imc.reference.ReferenceMarketDataField
import com.ingalys.imc.summary.Summary
import algotrader.api.source.summary._

case class Ticker(lastPrice: Double)
case class StopLoss()
trait Agent extends NativeTradingAgent {

  val inDe: InstrumentDescriptor

  //var hedgeId:String = "S50U21@TFEX""
  //var hedgeInstrument:InstrumentDescriptor = getService[InstrumentInfoService].getInstrumentByUniqueId("TFEX-EMAPI-HMM-PROXY", hedgeId) // "SET-EMAPI-HMM-PROXY" for SET

  sendLimitOrder(
    inDe,
    0,
    200L,
    0.19d
  )

  onMessage {

    case Start =>
  }

  onOrder {

    case Nak(t) =>
  }

  /*
  summarySource.map(_.modeStr.get).onUpdate(status => {
        if (status == "Startup") {
          mktStatus() = "Startup"
        } else if (status == "Pre-Open1") {
          mktStatus() = "Pre-Open1"
        } else if (status == "Open1") {
          mktStatus() = "Open1"
        } else if (status == "Intermission") {
          mktStatus() = "Intermission"
        } else if (status == "Pre-Open2") {
          mktStatus() = "Pre-Open2"
        } else if (status == "Open2") {
          mktStatus() = "Open2"
        } else if (status == "Pre-close") {
          mktStatus() = "Pre-close"
        } else if (status == "OffHour") {
          mktStatus() = "OffHour"
        } else if (status == "Closed") {
          mktStatus() = "Closed"
        }  else if (status == "Closed2") {
          mktStatus() = "Closed2"
        }  else if (status == "AfterMarket") {
          mktStatus() = "AfterMarket"
        }  else if (status == "CIRCUIT_BREAKER") {
          mktStatus() = "CIRCUIT_BREAKER"
        }  else if (status == "Pre-OpenTemp") {
          mktStatus() = "Pre-OpenTemp"
        }  else {
          mktStatus() = ""
        }
      })
    }
   */
}
