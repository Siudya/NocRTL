package nocrtl.router.buffer
import chisel3._
import chisel3.util._
import nocrtl.bundle.{BodyFlit, HeadFlit, VnLinkBundle}
import nocrtl.params.{NocParamsKey, VnParams}
import org.chipsalliance.cde.config.Parameters

class BufferComputeReqBundle(implicit p:Parameters) extends Bundle {
  val req = Output(Bool())
  val ack = Input(Bool())
  val dest = Output(UInt(p(NocParamsKey).idBits.W))
  val curOPort = Output(UInt(p(NocParamsKey).portIdBits.W))
  val nextOPort = Input(UInt(p(NocParamsKey).portIdBits.W))
  val nextOVCOH = Input(UInt(p(NocParamsKey).maxVc.W))
}

class ComputeResulInfoBundle(implicit p:Parameters) extends Bundle {
  val curPort = UInt(p(NocParamsKey).portIdBits.W)
  val nextPort = UInt(p(NocParamsKey).portIdBits.W)
  val nextVcoh = UInt(p(NocParamsKey).maxVc.W)
}

class BufferOutputBundle(vnP: VnParams)(implicit p:Parameters) extends Bundle {
  val flit = new BodyFlit(vnP)
  val port = UInt(p(NocParamsKey).portIdBits.W)
  val vcoh = UInt(p(NocParamsKey).maxVc.W)
}

class InputBuffer(vnP: VnParams)(implicit p:Parameters) extends Module {
  val io = IO(new Bundle{
    val link = Flipped(new VnLinkBundle(vnP))
    val outs = Vec(vnP.vcs.size, Decoupled(new BufferOutputBundle(vnP)))
    val comp = Vec(vnP.vcs.size, new BufferComputeReqBundle)
  })
  private val vcs = vnP.vcs.map(vc => Module(new Queue(gen = new BodyFlit(vnP), entries = vc.bufSize)))
  private val infoBits = Reg(Vec(vnP.vcs.size, new ComputeResulInfoBundle))
  private val infoValid = RegInit(VecInit(Seq.fill(vnP.vcs.size)(false.B)))

  private val vcBufDeqVldVec = Wire(Vec(vcs.size, Bool()))
  private val vcBufDeqTailVec = Wire(Vec(vcs.size, Bool()))
  io.link.crdt.valid := vcBufDeqVldVec.asUInt.orR
  io.link.crdt.bits.token := vcBufDeqVldVec.asUInt
  io.link.crdt.bits.tail := vcBufDeqTailVec.asUInt
  for(i <- vcs.indices) {
    vcs(i).io.enq.valid := io.link.flit.valid && io.link.flit.bits.vcoh(i)
    vcs(i).io.enq.bits := io.link.flit
    vcBufDeqVldVec(i) := vcs(i).io.deq.fire
    vcBufDeqTailVec(i) := vcs(i).io.deq.bits.tail

    when(vcs(i).io.enq.valid) {
      assert(vcs(i).io.enq.ready, s"Input buffer overflow of vc $i")
    }
    when(vcs(i).io.enq.valid && vcs(i).io.enq.bits.head && vnP.atomicBuf.B) {
      assert(vcs(i).io.deq.valid, s"Illegal enqueing to atomic input buffer of vc $i")
    }

    val headView = vcs(i).io.deq.bits.asTypeOf(new HeadFlit(vnP))
    io.comp(i).req := !infoValid(i) && vcs(i).io.deq.valid && vcs(i).io.deq.bits.head
    io.comp(i).dest := headView.dest
    io.comp(i).curOPort := headView.port

    when(io.comp(i).req && io.comp(i).ack) {
      infoValid(i) := true.B
      infoBits(i).curPort := headView.port
      infoBits(i).nextPort := io.comp(i).nextOPort
      infoBits(i).nextVcoh := io.comp(i).nextOVCOH
    }.elsewhen(vcs(i).io.deq.fire && vcs(i).io.deq.bits.tail) {
      infoValid(i) := false.B
    }

    val headWire = WireInit(headView)
    headWire.port := Mux(headView.head, infoBits(i).nextPort, headView.port)
    headWire.vcoh := Mux(headView.head, infoBits(i).nextVcoh, headView.vcoh)
    io.outs(i).valid := vcs(i).io.deq.valid && infoValid(i)
    io.outs(i).bits.flit := headWire.asTypeOf(io.outs(i).bits.flit)
    io.outs(i).bits.port := infoBits(i).curPort
    io.outs(i).bits.vcoh := infoBits(i).nextVcoh
  }
}
