package nocrtl.router.port

import chisel3._
import chisel3.util._
import nocrtl.params.PortParams
import org.chipsalliance.cde.config.Parameters

class Port(portP: PortParams)(implicit p:Parameters) extends Module {
  val links = portP.link.map(ln => IO(new LinkBufferExternalPortBundle(ln)))
  val vnPortMap: Map[String, LinkBufferVnPortBundle] = portP.link.flatMap(_.vns.map(vn => (vn.typeStr, IO(new LinkBufferVnPortBundle(vn))))).toMap
  vnPortMap.foreach({case(a, b) => b.suggestName(s"vn_${a.toLowerCase}")})

  private val linkCtrls = portP.link.map(lnk => Module(new LinkBuffer(lnk)))
  private val internalVnPorts = linkCtrls.flatMap(_.vnPortMap)

  for(lnid <- links.indices) links(lnid) <> linkCtrls(lnid).link
  for((vn, hw) <- internalVnPorts) vnPortMap(vn) <> hw

  def <>(that:Port):Unit = {
    for((a, b) <- that.links.zip(this.links)) {
      a.inward <> b.outward
      b.inward <> a.outward
    }
  }
}
