package nocrtl.router.port

import chisel3._
import chisel3.util._
import nocrtl.bundle.{BodyFlit, LinkBundle, LinkPayload}
import nocrtl.params.{LinkParams, VnParams}
import nocrtl.router.buffer.{BufferState, BufferStateOutputBundle, InputBuffer}
import nocrtl.utils.RRArbiterWithReset
import org.chipsalliance.cde.config.Parameters

class LinkBufferExternalPortBundle(lnkP:LinkParams)(implicit p:Parameters) extends Bundle {
  val inward = Flipped(new LinkBundle(lnkP))
  val outward = new LinkBundle(lnkP)
}

class LinkBufferSendRequestBundle(vnP: VnParams)(implicit p:Parameters) extends Bundle {
  val flit = Flipped(Decoupled(UInt(vnP.flitBits.W)))
  val state = MixedVec(vnP.vcs.map(vnP => new BufferStateOutputBundle(vnP)))
}

class LinkBufferVnPortBundle(val vnP:VnParams)(implicit p:Parameters) extends Bundle {
  val recv = Vec(vnP.vcs.size, Decoupled(UInt(vnP.flitBits.W)))
  val send = new LinkBufferSendRequestBundle(vnP)
}

class LinkBuffer(lnkP:LinkParams)(implicit p:Parameters) extends Module {
  val link = IO(new LinkBufferExternalPortBundle(lnkP))
  val vnPortMap: Map[String, LinkBufferVnPortBundle] = lnkP.vns.map(vn => (vn.typeStr, IO(new LinkBufferVnPortBundle(vn)))).toMap
  vnPortMap.foreach({case(a, b) => b.suggestName(s"vn_${a.toLowerCase}")})

  private val vnbufs = lnkP.vns.map(vn => (vn.typeStr, Module(new InputBuffer(vn))))
  vnbufs.foreach({case(a, b) => b.suggestName(s"ibuf_${a.toLowerCase}")})
  private val grantVec = Wire(Vec(lnkP.vns.size, Bool()))
  link.inward.crdt.valid := grantVec.asUInt.orR
  for(vnid <- lnkP.vns.indices) {
    val buf = vnbufs(vnid)._2
    buf.io.link.flit.valid := link.inward.flit.valid && link.inward.flit.bits.vnoh(vnid)
    buf.io.link.flit.bits := link.inward.flit.bits.data
    grantVec(vnid) := buf.io.link.crdt.valid
    link.inward.crdt.bits.token(vnid) := buf.io.link.crdt.bits.token
    link.inward.crdt.bits.tail(vnid) := buf.io.link.crdt.bits.tail
    vnPortMap(lnkP.vns(vnid).typeStr).recv <> buf.io.outs
  }

  private val vnBufStats = lnkP.vns.map(vn => (vn.typeStr, Module(new BufferState(vn))))
  private val vnOutBufs = lnkP.vns.map(vn => (vn.typeStr, Module(new Queue(UInt(vn.flitBits.W), entries = 1, pipe = true))))
  vnBufStats.foreach({case(a, b) => b.suggestName(s"obuf_stat_${a.toLowerCase}")})
  vnOutBufs.foreach({case(a, b) => b.suggestName(s"obuf_${a.toLowerCase}")})
  for(vnid <- lnkP.vns.indices) {
    val bufStat = vnBufStats(vnid)._2
    val outBuf = vnOutBufs(vnid)._2
    val sendPort = vnPortMap(lnkP.vns(vnid).typeStr).send
    bufStat.io.grant.valid := link.outward.crdt.valid
    bufStat.io.grant.bits.token := link.outward.crdt.bits.token(vnid)
    bufStat.io.grant.bits.tail := link.outward.crdt.bits.tail(vnid)
    sendPort.state <> bufStat.io.state

    val sendFlitView = sendPort.flit.bits.asTypeOf(new BodyFlit(lnkP.vns(vnid)))
    bufStat.io.alloc.valid := sendPort.flit.fire
    bufStat.io.alloc.bits.vcoh := sendFlitView.vcoh
    bufStat.io.alloc.bits.head := sendFlitView.head
    bufStat.io.alloc.bits.tail := sendFlitView.tail
    outBuf.io.enq <> sendPort.flit
  }

  private val outArb = Module(new RRArbiterWithReset(new LinkPayload(lnkP), lnkP.vns.size))
  outArb.io.out.ready := true.B
  link.outward.flit.valid := outArb.io.out.valid
  link.outward.flit.bits := outArb.io.out.bits
  for(vnid <- lnkP.vns.indices) {
    val buf = vnOutBufs(vnid)._2
    outArb.io.in(vnid).valid := buf.io.deq.valid
    outArb.io.in(vnid).bits.data := buf.io.deq.bits
    outArb.io.in(vnid).bits.vnoh := (1 << vnid).U
    buf.io.deq.ready := outArb.io.in(vnid).ready
  }
}
