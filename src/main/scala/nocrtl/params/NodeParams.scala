package nocrtl.params

import chisel3.util.log2Ceil

import scala.collection.mutable

case class VcParams (
  id:Int = 0,
  bufSize:Int = 2
) {
}

case class VnParams(
  width:Int = 128,
  vcs:Seq[VcParams] = Seq(),
  atomicBuf:Boolean = true
) {
  require(vcs.nonEmpty)
  def apply(vc:Int):VcParams = {
    require(vcs.size > vc)
    vcs(vc)
  }
  lazy val vcIdBits:Int = log2Ceil(vcs.size)
}

case class LinkParams (
  width:Int = 128,
  pipe:Int = 0,
  vns:Map[String, VnParams] = Map(),
) {
  def apply(vnn: String):VnParams = {
    require(vns.contains(vnn))
    vns(vnn)
  }
  lazy val crdtLinkBits = 1 + vns.size
}

case class PortParams (
  lks:Seq[LinkParams] = Seq(),
) {
  def apply(link:Int):LinkParams = {
    require(lks.size > link)
    lks(link)
  }
}

case class NodeParams (
  name:String = "",
  ports: Map[String, PortParams] = Map(),
  indices:Seq[Int] = Seq(0)
) {
  require(ports.nonEmpty)
  require(indices.nonEmpty)
  def apply(pn: String): PortParams = {
    require(ports.contains(pn))
    ports(pn)
  }
}