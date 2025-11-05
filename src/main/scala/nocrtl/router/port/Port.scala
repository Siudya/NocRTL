package nocrtl.router.port

import chisel3._
import chisel3.util._
import nocrtl.bundle.BodyFlit
import nocrtl.params.{NocParamsKey, PortParams}
import nocrtl.router.buffer.BufferOutputBundle
import org.chipsalliance.cde.config.Parameters

class Port(portP: PortParams)(implicit p:Parameters) extends Module {
  val links = portP.link.map(ln => (ln.vnStr, IO(new LinkBufferExternalPortBundle(ln)))).toMap
  links.foreach(lnk => lnk._2.suggestName(s"link_${lnk._2.inward.lnkP.vnStr.toLowerCase}"))

  private val allVns = portP.link.flatMap(_.vns)
  val vnToCompMap = allVns.map(vn => (vn.typeStr, IO(new LinkBufferToComputeBundle(vn)))).toMap
  vnToCompMap.foreach({case(a, b) => b.suggestName(s"vn_${a.toLowerCase}_comp")})

  val vnToInwardMap = allVns.map(vn => (vn.typeStr, IO(Vec(vn.vcs.size, Decoupled(new BufferOutputBundle(vn)))))).toMap
  vnToInwardMap.foreach({case(a, b) => b.suggestName(s"vn_${a.toLowerCase}_inward")})

  val vnToOutwardMap = allVns.map(vn => (vn.typeStr, IO(Vec(vn.vcs.size, Flipped(Decoupled(new BodyFlit(vn))))))).toMap
  vnToOutwardMap.foreach({case(a, b) => b.suggestName(s"vn_${a.toLowerCase}_outward")})

  private val linkCtrls = portP.link.map(lnk => (lnk.vnStr, Module(new LinkBuffer(lnk)))).toMap

  private val internalVnCompPorts = linkCtrls.flatMap(_._2.vnToCompMap)
  private val internalVnInwardPorts = linkCtrls.flatMap(_._2.vnToInwardMap)
  private val internalVnOutwardPorts = linkCtrls.flatMap(_._2.vnToOutwardMap)

  for(key <- links.keys) links(key) <> linkCtrls(key).link

  for(vn <- allVns) {
    val key = vn.typeStr
    vnToCompMap(key) <> internalVnCompPorts(key)
    vnToInwardMap(key) <> internalVnInwardPorts(key)
    vnToOutwardMap(key) <> internalVnOutwardPorts(key)
  }

  def <>(that:Port):Unit = {
    for(key <- that.links.keys) {
      this.links(key).inward <> that.links(key).outward
      this.links(key).outward <> that.links(key).inward
    }
  }
}
