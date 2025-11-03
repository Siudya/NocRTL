package nocrtl.params

import chisel3.util.log2Ceil
import nocrtl.router.compute.{RouteCompute, VirtualChannelSelect}
import org.chipsalliance.cde.config.Field

import scala.collection.mutable

case object NocParamsKey extends Field[NocParameters]

object NocParameters {
  private val networkMap = mutable.HashMap[() => Seq[NodeParams], Seq[NodeParams]]()
  val maxVcMap = mutable.HashMap[() => Seq[NodeParams], Int]()

  def getNodes(gen: () => Seq[NodeParams]):Seq[NodeParams] = {
    if(networkMap.contains(gen)) {
      networkMap.addOne((gen, gen()))
      val nodes = networkMap(gen)
      val maxVc = nodes.flatMap(_.ports).flatMap(_.link).flatMap(_.vns).map(_.vcs.size).max
      maxVcMap.addOne((gen, maxVc))
    }
    networkMap(gen)
  }
}

case class NocParameters (
  networkGen: () => Seq[NodeParams] = () => Seq(),
  vnIdMap: Map[String, Int] = Map(),
  portIdMap: Map[String, Int] = Map(),
  rcMod: () => Option[RouteCompute] = () => None,
  vsMod: Int => Option[VirtualChannelSelect] = (_:Int) => None
) {
  lazy val nodes: Seq[NodeParams] = NocParameters.getNodes(networkGen)
  lazy val size: Int = nodes.size
  lazy val idBits: Int = log2Ceil(size)
  lazy val portIdBits:Int = log2Ceil(nodes.flatMap(_.ports.map(_.dirStr)).map(portIdMap).max)
  lazy val maxVc:Int = NocParameters.maxVcMap(networkGen)
}