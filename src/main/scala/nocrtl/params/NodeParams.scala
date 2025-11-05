package nocrtl.params

import nocrtl.bundle.BodyFlit

import scala.collection.mutable

case class VcParams (
  id:Int = 0,
  bufSize:Int = 2
) {
}

case class VnParams(
  width:Int = 128,
  vcs:Seq[VcParams] = Seq(),
  atomicBuf:Boolean = false,
  typeStr:String = ""
) {
  require(vcs.nonEmpty)
  def apply(vc:Int):VcParams = {
    require(vcs.size > vc)
    vcs(vc)
  }
  lazy val flitBits = new BodyFlit(this).getWidth
  var link: Option[LinkParams] = None
}

case class LinkParams (
  pipe:Int = 0,
  vns:Seq[VnParams] = Seq(),
) {
  lazy val vnsMap = vns.map(vn => (vn.typeStr, vn)).toMap
  lazy val hasAtomicBuf = vns.map(_.atomicBuf).reduce(_ || _)
  lazy val maxVc = vns.map(_.vcs.size).max
  lazy val flitBits = vns.map(vn => vn.flitBits).max
  vns.foreach(vn => vn.link = Some(this))

  def apply(vns: String):VnParams = {
    require(vnsMap.contains(vns))
    vnsMap(vns)
  }
  val ports = mutable.Seq[PortParams]()
  def <> (port:PortParams):LinkParams = {
    require(ports.size < 2)
    ports.appended(port)
    this
  }
  lazy val vnStr = vns.map(_.typeStr).mkString("_")
}

case class PortParams (
  dirStr:String = ""
) {
  var node: Option[NodeParams] = None
  val link = mutable.Seq[LinkParams]()
  def <> (lnk:LinkParams):Unit = {
    link.appended(lnk)
    require(lnk.ports.size < 2)
    lnk.ports.appended(this)
    val allVnTypes = link.flatMap(_.vns.map(_.typeStr))
    require(allVnTypes.distinct.size == allVnTypes.size)
  }
}

case class NodeParams (
  name:String = "",
  ports: Seq[PortParams] = Seq(),
  id:Int = 0
) {
  require(ports.nonEmpty)
  lazy val portsMap = ports.map(port => (port.dirStr, port)).toMap
  def apply(ps: String): PortParams = {
    require(portsMap.contains(ps))
    portsMap(ps)
  }
  ports.foreach(_.node = Some(this))
}