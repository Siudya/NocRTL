package nocrtl.router.buffer
import chisel3._
import chisel3.util._
import nocrtl.bundle.{FlitBundle, LinkBundle}
import nocrtl.params.VnParams
import org.chipsalliance.cde.config.Parameters

class InputBuffer(vnP: VnParams)(implicit p:Parameters) extends Module {
  val io = IO(new Bundle{
    val link = Flipped(new LinkBundle(vnP))
    val outs = Vec(vcs.size, Decoupled(new FlitBundle(vnP)))
  })
  private val vcs = vnP.vcs.map(vc => Module(new Queue(gen = new FlitBundle(vnP), entries = vc.bufSize)))
  private val vcBufDeqVldVec = Wire(Vec(vcs.size, Bool()))
  private val vcBufDeqTailVec = io.link.tail.map(_ => Wire(Vec(vcs.size, Bool())))
  io.link.grnt := vcBufDeqVldVec.asUInt.orR
  io.link.tokn.foreach(_ := vcBufDeqVldVec.asUInt)
  io.link.tail.foreach(_  := vcBufDeqTailVec.get.asUInt)
  for(i <- vcs.indices) {
    vcs(i).io.enq.valid := io.link.vlid && io.link.flit.vc === i.U
    vcs(i).io.enq.bits := io.link.flit
    vcBufDeqVldVec(i) := vcs(i).io.deq.fire
    vcBufDeqTailVec.foreach(t => t(i) := vcs(i).io.deq.bits.tail)
    io.outs(i) <> vcs(i).io.deq
    when(vcs(i).io.enq.valid) {
      assert(vcs(i).io.enq.ready, s"Input buffer overflow of vc $i")
    }
    when(vcs(i).io.enq.valid && vcs(i).io.enq.bits.head && vnP.atomicBuf.B) {
      assert(vcs(i).io.deq.valid, s"Illegal enqueing to atomic input buffer of vc $i")
    }
  }
}
