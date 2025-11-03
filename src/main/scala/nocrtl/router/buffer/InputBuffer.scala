package nocrtl.router.buffer
import chisel3._
import chisel3.util._
import nocrtl.bundle.{BodyFlit, VnLinkBundle}
import nocrtl.params.VnParams

class InputBuffer(vnP: VnParams) extends Module {
  val io = IO(new Bundle{
    val link = Flipped(new VnLinkBundle(vnP))
    val outs = Vec(vnP.vcs.size, Decoupled(UInt(vnP.flitBits.W)))
  })
  private val vcs = vnP.vcs.map(vc => Module(new Queue(gen = UInt(vnP.flitBits.W), entries = vc.bufSize)))
  private val vcBufDeqVldVec = Wire(Vec(vcs.size, Bool()))
  private val vcBufDeqTailVec = Wire(Vec(vcs.size, Bool()))
  io.link.crdt.valid := vcBufDeqVldVec.asUInt.orR
  io.link.crdt.bits.token := vcBufDeqVldVec.asUInt
  io.link.crdt.bits.tail := vcBufDeqTailVec.asUInt
  private val bodyFlitView = io.link.flit.bits.asTypeOf(new BodyFlit(vnP))
  for(i <- vcs.indices) {
    vcs(i).io.enq.valid := io.link.flit.valid && bodyFlitView.vcoh(i)
    vcs(i).io.enq.bits := io.link.flit
    vcBufDeqVldVec(i) := vcs(i).io.deq.fire
    vcBufDeqTailVec(i) := vcs(i).io.deq.bits.tail
    io.outs(i) <> vcs(i).io.deq
    when(vcs(i).io.enq.valid) {
      assert(vcs(i).io.enq.ready, s"Input buffer overflow of vc $i")
    }
    when(vcs(i).io.enq.valid && vcs(i).io.enq.bits.head && vnP.atomicBuf.B) {
      assert(vcs(i).io.deq.valid, s"Illegal enqueing to atomic input buffer of vc $i")
    }
  }
}
