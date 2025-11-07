package nocrtl.params

import chisel3.util.log2Ceil
import nocrtl.router.compute.{RouteCompute, VirtualChannelSelect}
import org.chipsalliance.cde.config.{Field, Parameters}

import scala.collection.mutable

case object NocParamsKey extends Field[NocParameters]

object NocParameters {
  private val networkMap = mutable.HashMap[() => Seq[NodeParams], Seq[NodeParams]]()
  val maxVcMap = mutable.HashMap[() => Seq[NodeParams], Int]()

  def getNodes(gen: () => Seq[NodeParams]):Seq[NodeParams] = {
    if(!networkMap.contains(gen)) {
      val nodes = gen()
      networkMap.addOne((gen, nodes))
      println(nodes.flatMap(_.ports).flatMap(_.link).size)
      val maxVc = nodes.flatMap(_.ports).flatMap(_.link).flatMap(_.vns).map(_.vcs.size).max
      maxVcMap.addOne((gen, maxVc))
    }
    networkMap(gen)
  }

  private val nodes = mutable.HashMap[Int, NodeParams]()
  private val links = mutable.HashMap[Int, LinkParams]()
  private val ports = mutable.HashMap[Int, PortParams]()
}

case class NocParameters (
  networkGen: () => Seq[NodeParams] = () => Seq(),
  vnSeq:Seq[String] = Seq(),
  portSeq:Seq[String] = Seq(),
  rcMod: Option[Parameters => RouteCompute] = None,
  vsMod: Option[(Int, Int, Parameters) => VirtualChannelSelect] = None
) {
  lazy val nodes: Seq[NodeParams] = NocParameters.getNodes(networkGen)
  lazy val size: Int = nodes.size
  lazy val idBits: Int = log2Ceil(size)
  lazy val portIdBits:Int = log2Ceil(nodes.flatMap(_.ports.map(_.dirStr)).map(portIdMap).max)
  lazy val vnIdBits:Int = log2Ceil(nodes.flatMap(_.ports).flatMap(_.link).flatMap(_.vns.map(_.typeStr)).map(vnIdMap).max)
  lazy val maxVc:Int = NocParameters.maxVcMap(networkGen)
  lazy val routerSpaceBits: Int = 16
  lazy val vnSpaceBits:Int = routerSpaceBits - vnIdBits
  lazy val vnIdMap = vnSeq.zipWithIndex.toMap
  lazy val portIdMap = portSeq.zipWithIndex.toMap
}