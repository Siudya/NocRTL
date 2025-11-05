package nocrtl.router.port

import chisel3._
import chisel3.util._
import nocrtl.bundle.{BodyFlit, LinkBundle, LinkPayload}
import nocrtl.params.{LinkParams, NocParamsKey, VnParams}
import nocrtl.router.buffer._
import nocrtl.utils.RRArbiterWithReset
import org.chipsalliance.cde.config.Parameters

class LinkBufferExternalPortBundle(lnkP:LinkParams)(implicit p:Parameters) extends Bundle {
  val inward = Flipped(new LinkBundle(lnkP))
  val outward = new LinkBundle(lnkP)
}

class LinkBufferToComputeBundle(vnP:VnParams)(implicit p:Parameters) extends Bundle {
  val va = Vec(vnP.vcs.size, new BufferComputeReqBundle)
  val bs = MixedVec(vnP.vcs.map(vc => new BufferStateBundle(vc)))
}

class LinkBuffer(lnkP:LinkParams)(implicit p:Parameters) extends Module {
  val link = IO(new LinkBufferExternalPortBundle(lnkP))

  val vnToCompMap = lnkP.vns.map(vn => (vn.typeStr, IO(new LinkBufferToComputeBundle(vn)))).toMap
  vnToCompMap.foreach({case(a, b) => b.suggestName(s"vn_${a.toLowerCase}_comp")})

  val vnToInwardMap = lnkP.vns.map(vn => (vn.typeStr, IO(Vec(vn.vcs.size, Decoupled(new BufferOutputBundle(vn)))))).toMap
  vnToInwardMap.foreach({case(a, b) => b.suggestName(s"vn_${a.toLowerCase}_inward")})

  val vnToOutwardMap = lnkP.vns.map(vn => (vn.typeStr, IO(Vec(vn.vcs.size, Flipped(Decoupled(new BodyFlit(vn))))))).toMap
  vnToOutwardMap.foreach({case(a, b) => b.suggestName(s"vn_${a.toLowerCase}_outward")})

  private val vnbufs = lnkP.vns.map(vn => (vn.typeStr, Module(new InputBuffer(vn))))
  vnbufs.foreach({case(a, b) => b.suggestName(s"ibuf_vn_${a.toLowerCase}")})

  private val vnIdMap = p(NocParamsKey).vnIdMap

  private val grantVec = Wire(Vec(lnkP.vns.size, Bool()))
  link.inward.crdt.valid := grantVec.asUInt.orR
  for(vnid <- lnkP.vns.indices) {
    val buf = vnbufs(vnid)._2
    val key = lnkP.vns(vnid).typeStr
    buf.io.link.flit.valid := link.inward.flit.valid && link.inward.flit.bits.vnid === vnIdMap(key).U
    buf.io.link.flit.bits := link.inward.flit.bits.data
    grantVec(vnid) := buf.io.link.crdt.valid
    link.inward.crdt.bits.token(vnid) := buf.io.link.crdt.bits.token
    link.inward.crdt.bits.tail(vnid) := buf.io.link.crdt.bits.tail
    vnToInwardMap(key) <> buf.io.outs
    vnToCompMap(key).va <> buf.io.comp
  }

  private val vnBufStats = lnkP.vns.map(vn => (vn.typeStr, Module(new BufferState(vn))))
  private val vnOutBufs = lnkP.vns.map(vn => (vn.typeStr, Module(new Queue(new BodyFlit(vn), entries = 1, pipe = true))))
  vnBufStats.foreach({case(a, b) => b.suggestName(s"obuf_stat_${a.toLowerCase}")})
  vnOutBufs.foreach({case(a, b) => b.suggestName(s"obuf_${a.toLowerCase}")})

  for(vnid <- lnkP.vns.indices) {
    val bufStat = vnBufStats(vnid)._2
    val outBuf = vnOutBufs(vnid)._2
    val key = lnkP.vns(vnid).typeStr
    bufStat.io.grant.valid := link.outward.crdt.valid
    bufStat.io.grant.bits.token := link.outward.crdt.bits.token(vnid)
    bufStat.io.grant.bits.tail := link.outward.crdt.bits.tail(vnid)
    vnToCompMap(key).bs <> bufStat.io.state

    bufStat.io.alloc.valid := outBuf.io.enq.fire
    bufStat.io.alloc.bits := outBuf.io.enq.bits.vcoh
    outBuf.io.enq <> vnToOutwardMap(key)
  }

  private val outArb = Module(new RRArbiterWithReset(new LinkPayload(lnkP), lnkP.vns.size))
  outArb.io.out.ready := true.B
  link.outward.flit.valid := outArb.io.out.valid
  link.outward.flit.bits := outArb.io.out.bits
  for(vnid <- lnkP.vns.indices) {
    val buf = vnOutBufs(vnid)._2
    outArb.io.in(vnid).valid := buf.io.deq.valid
    outArb.io.in(vnid).bits.data := buf.io.deq.bits
    outArb.io.in(vnid).bits.vnid := vnIdMap(lnkP.vns(vnid).typeStr).U
    buf.io.deq.ready := outArb.io.in(vnid).ready
  }
}
