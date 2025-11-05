package nocrtl.network.inout

import chisel3._
import chisel3.experimental.noPrefix
import chisel3.util._
import nocrtl.bundle.Packet
import nocrtl.params.{NocParamsKey, PortParams, VnParams}
import nocrtl.router.port.{BaseRouterPort, Port}
import org.chipsalliance.cde.config.Parameters

class IcnBundle(vnP: VnParams)(implicit p:Parameters) extends Bundle {
  val rx = Vec(vnP.vcs.size, Flipped(Decoupled(new Packet(vnP))))
  val tx = Vec(vnP.vcs.size, Decoupled(new Packet(vnP)))

  def <>(that:DevBundle):Unit = {
    this.rx <> that.tx
    this.tx <> that.rx
  }
}

class DevBundle(vnP: VnParams)(implicit p:Parameters) extends Bundle {
  val rx = Vec(vnP.vcs.size, Flipped(Decoupled(new Packet(vnP))))
  val tx = Vec(vnP.vcs.size, Decoupled(new Packet(vnP)))

  def <>(that:IcnBundle):Unit = {
    this.rx <> that.tx
    this.tx <> that.rx
  }
}

class InoutController(portP: PortParams)(implicit p:Parameters) extends BaseRouterPort(portP) {

  val router = IO(Input(UInt(p(NocParamsKey).idBits.W)))

  val ioMap = allVns.map(vn => (vn.typeStr, IO(new IcnBundle(vn)))).toMap
  ioMap.foreach({case(a, b) => b.suggestName(s"io_${a.toLowerCase}")})

  private val vport = Module(new Port(portP))

  for(vn <- allVns) noPrefix {
    val key = vn.typeStr
    vport.links(key) <> links(key)
    vport.vnToCompMap(key).va.foreach(va => {
      va := DontCare
      va.ack := true.B
    })
    for(vc <- vn.vcs.indices) noPrefix {
      val splitter = Module(new PacketSplitter(vn))
      val rc = Module(p(NocParamsKey).rcMod.get(p))
      rc.io.router := router
      rc.io.dest := splitter.io.dest
      splitter.io.pack <> ioMap(key).rx(vc)
      splitter.io.port := rc.io.port
      splitter.io.vcoh.foreach(_ := (1L << vc).U)
      vport.vnToOutwardMap(key)(vc) <> splitter.io.flit

      val merger = Module(new FlitMerger(vn))
      merger.io.flit.valid := vport.vnToInwardMap(key)(vc).valid
      merger.io.flit.bits := vport.vnToInwardMap(key)(vc).bits.flit
      vport.vnToInwardMap(key)(vc).ready := merger.io.flit.ready
      ioMap(key).tx(vc) <> merger.io.pack
    }
  }
}
