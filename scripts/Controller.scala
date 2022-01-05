package mrt

import algotrader.api.Messages.{Load, Start}
import algotrader.api.NativeController
import horizontrader.plugins.hmm.connections.service.IDictionaryProvider
import horizontrader.services.instruments.{InstrumentDescriptor, InstrumentInfoService}
import algotrader.api.source.summary._
import com.hsoft.datamaster.product.{Derivative, ProductTypes}
import com.ingalys.imc.summary.Summary
import scala.collection.JavaConverters._

import scala.language.higherKinds

case object StopBot
case object StartBot

trait Controller extends NativeController {
  val portfolioId: String
  val hedgePortfolio: String
  val ulInstrument: InstrumentDescriptor
  val hedgeInstrument: InstrumentDescriptor //PTT@XBKK ?? Nop will find a way // String => SET-EMAPI-HMM-PROXY|ADVANC@XBKK
  val dictionaryService: IDictionaryProvider = getService[IDictionaryProvider]
  val exchange                               = "SET-EMAPI-HMM-PROXY"

  var agent: TradingAgentComponent = null

  onMessage {

    case Load =>
      agent = createTradingAgent(
        "Agent.scala",
        Map(
          "portfolioId"       -> portfolioId,
          "hedgePortfolio"    -> hedgePortfolio,
          "ulInstrument"      -> ulInstrument,
          "hedgeInstrument"   -> hedgeInstrument,
          "dictionaryService" -> dictionaryService
        )
      )

    case Start =>
      source[Summary]
        .get(ulInstrument)
        .map(p => {
          p.modeStr.get
        })
        .onUpdate {
          case s @ "Pre-close" =>
            log.info(s"Controller. UL ${ulInstrument.getUniqueId} Start bot Pre-open1/2/preclose  $s")
            Lock.removeStopBot(ulInstrument.getUniqueId)
            agent ! StartBot

          case s @ _ =>
            log.info(s"Controller. UL ${ulInstrument.getUniqueId} Stop bot $s")
            Lock.addStopBot(ulInstrument.getUniqueId)
            agent ! StopBot
        }

      dictionaryService.getDictionary
        .getProducts(null)
        .values()
        .asScala
        .filter(_.getProductType == ProductTypes.WARRANT)
        .map(p => p.asInstanceOf[Derivative])
        .filter(d => d.getUlId == ulInstrument.getUniqueId)
        .foreach(d => {
          val dwInstrument = getService[InstrumentInfoService].getInstrumentByUniqueId(exchange, d.getId)

          source[Summary]
            .get(dwInstrument)
            .map(_.modeStr.get)
            .onUpdate {
              case s @ "Pre-close" =>
                log.info(s"Controller. DW ${dwInstrument.getUniqueId} Start bot Pre-open1/2/preclose $s")
                Lock.removeStopBot(ulInstrument.getUniqueId)
                agent ! StartBot

              case s @ _ =>
                log.info(s"Controller. DW ${dwInstrument.getUniqueId} Stop bot $s")
                Lock.addStopBot(ulInstrument.getUniqueId)
                agent ! StopBot
            }
        })
  }
}
