package nocrtl.router.buffer
import chisel3._
import chisel3.util._
import nocrtl.bundle.VnLinkCredit
import nocrtl.params.{VcParams, VnParams}

class BufferStateOutputBundle(val vcP: VcParams) extends Bundle {
  val avail = Output(Bool())
  val entry = Output(UInt((log2Ceil(vcP.bufSize)+ 1).W))
}

class BufferStateAllocBundle(vnP: VnParams) extends Bundle {
  val vcoh = Input(UInt(vnP.vcs.size.W))
  val head = Input(Bool())
  val tail = Input(Bool())
}

class BufferState(vnP: VnParams) extends Module {
  val io = IO(new Bundle {
    val grant = Input(Valid(new VnLinkCredit(vnP)))
    val alloc = Input(Valid(new BufferStateAllocBundle(vnP)))
    val state = MixedVec(vnP.vcs.map(vc => new BufferStateOutputBundle(vc)))
  })
  private val tokenVec = RegInit(MixedVec(vnP.vcs.map(vc => vc.bufSize.U((log2Ceil(vc.bufSize)+ 1).W))))
  private val takenVec = RegInit(VecInit(Seq.fill(vnP.vcs.size)(false.B)))

  for(i <- vnP.vcs.indices) {
    val grant = io.grant.valid && io.grant.bits.token(i)
    val alloc = io.alloc.valid && io.alloc.bits.vcoh(i)
    when(alloc && io.alloc.bits.head) {
      takenVec(i) := true.B
    }.elsewhen(grant && io.alloc.bits.tail) {
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
    io.state(i).entry := tokenVec(i)
  }
}
