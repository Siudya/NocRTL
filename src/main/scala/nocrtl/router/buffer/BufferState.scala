package nocrtl.router.buffer
import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.Parameters
import nocrtl.bundle.{FlitBundle, LinkBundle}
import nocrtl.params.VnParams

class BufferStateGrantBundle(vnP: VnParams) extends Bundle {
  val vld   = Input(Bool())
  val tkn   = Option.when(vnP.vcs.size > 1)(Input((UInt(vnP.vcs.size.W))))
  val tail  = Option.when(vnP.atomicBuf)(Input(UInt(vnP.vcs.size.W)))
}

class BufferStateRequestBundle(vnP: VnParams)(implicit p:Parameters) extends Bundle {
  val vld = Input(Bool())
  val rdy = Output(Bool())
  val head = Input(Bool())
  val tail = Input(Bool())
}

class BufferState(vnP: VnParams)(implicit p:Parameters) extends Module {
  val io = IO(new Bundle {
    val grnt = new BufferStateGrantBundle(vnP)
    val req = Vec(vnP.vcs.size, new BufferStateRequestBundle(vnP))
    val cnts = Output(MixedVec(vnP.vcs.map(vc => UInt((log2Ceil(vc.bufSize)+ 1).W))))
  })
  private val tokenNumVec = RegInit(MixedVec(vnP.vcs.map(vc => vc.bufSize.U((log2Ceil(vc.bufSize)+ 1).W))))

  io.cnts := tokenNumVec

  for(i <- vnP.vcs.indices) {

  }
}
