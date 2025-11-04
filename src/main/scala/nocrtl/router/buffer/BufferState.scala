package nocrtl.router.buffer
import chisel3._
import chisel3.util._
import nocrtl.bundle.VnLinkCredit
import nocrtl.params.{VcParams, VnParams}

class BufferStateBundle(val vcP: VcParams) extends Bundle {
  val avail = Output(Bool())
  val take = Input(Bool())
}

class BufferState(vnP: VnParams) extends Module {
  val io = IO(new Bundle {
    val grant = Input(Valid(new VnLinkCredit(vnP)))
    val alloc = Input(Valid(UInt(vnP.vcs.size.W)))
    val state = MixedVec(vnP.vcs.map(vc => new BufferStateBundle(vc)))
  })
  private val tokenVec = RegInit(MixedVec(vnP.vcs.map(vc => vc.bufSize.U((log2Ceil(vc.bufSize)+ 1).W))))
  private val takenVec = RegInit(VecInit(Seq.fill(vnP.vcs.size)(false.B)))

  for(i <- vnP.vcs.indices) {
    val grant = io.grant.valid && io.grant.bits.token(i)
    val alloc = io.alloc.valid && io.alloc.bits.vcoh(i)
    when(io.state(i).take) {
      takenVec(i) := true.B
    }.elsewhen(grant && io.grant.bits.tail(i)) {
      takenVec(i) := false.B
    }
    when(grant || alloc) {
      tokenVec(i) := (tokenVec(i) +& grant.asUInt) - alloc.asUInt
    }
    if(vnP.atomicBuf) {
      io.state(i).avail := tokenVec(i).orR && !takenVec(i)
    } else {
      io.state(i).avail := tokenVec(i).orR
    }
  }
}
