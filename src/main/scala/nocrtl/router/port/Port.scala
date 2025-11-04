package nocrtl.router.port

import chisel3._
import chisel3.util._
import nocrtl.bundle.BodyFlit
import nocrtl.params.PortParams
import nocrtl.router.buffer.BufferOutputBundle
import org.chipsalliance.cde.config.Parameters

class Port(portP: PortParams)(implicit p:Parameters) extends Module {
  val links = portP.link.map(ln => IO(new LinkBufferExternalPortBundle(ln)))

  private val allVns = portP.link.flatMap(_.vns)
  val vnCompPortMap = allVns.map(vn => (vn.typeStr, IO(new LinkBufferToComputeBundle(vn)))).toMap
  vnCompPortMap.foreach({case(a, b) => b.suggestName(s"vn_${a.toLowerCase}_comp")})

  val vnInwardPortMap = allVns.map(vn => (vn.typeStr, IO(Vec(vn.vcs.size, Decoupled(new BufferOutputBundle(vn)))))).toMap
  vnInwardPortMap.foreach({case(a, b) => b.suggestName(s"vn_${a.toLowerCase}_inward")})

  val vnOutwardPortMap = allVns.map(vn => (vn.typeStr, IO(Vec(vn.vcs.size, Flipped(Decoupled(new BodyFlit(vn))))))).toMap
  vnOutwardPortMap.foreach({case(a, b) => b.suggestName(s"vn_${a.toLowerCase}_outward")})

  private val linkCtrls = portP.link.map(lnk => Module(new LinkBuffer(lnk)))

  private val internalVnCompPorts = linkCtrls.flatMap(_.vnCompPortMap).toMap
  private val internalVnInwardPorts = linkCtrls.flatMap(_.vnInwardPortMap).toMap
  private val internalVnOutwardPorts = linkCtrls.flatMap(_.vnOutwardPortMap).toMap

  for(lnid <- links.indices) links(lnid) <> linkCtrls(lnid).link

  for(vn <- allVns) {
    val key = vn.typeStr
    vnCompPortMap(key) <> internalVnCompPorts(key)
    vnInwardPortMap(key) <> internalVnInwardPorts(key)
    vnOutwardPortMap(key) <> internalVnOutwardPorts(key)
  }

  def <>(that:Port):Unit = {
    for((a, b) <- that.links.zip(this.links)) {
      a.inward <> b.outward
      b.inward <> a.outward
    }
  }
}
