package nocrtl.router.compute

import chisel3._
import chisel3.util._
import nocrtl.bundle._
import nocrtl.params.NocParamsKey
import org.chipsalliance.cde.config.Parameters

class VirtualChannelSelectInterface(implicit p:Parameters) extends Bundle {
  val outPort = Input(UInt(p(NocParamsKey).portIdBits.W))
  val candidateVc = Output(UInt(p(NocParamsKey).maxVc.W))
  val selectedVc = Output(UInt(p(NocParamsKey).maxVc.W))
}

abstract class VirtualChannelSelect(querySize:Int, router:Int)(implicit p:Parameters) extends Module {
  override val desiredName = s"vc_sel_r_$router"
  val io = IO(new Bundle {
    val query = Vec(querySize, new VirtualChannelSelectInterface)
    val s_axi_cfg = Flipped(new AxiLiteBundle)
  })
  private val awq = Module(new Queue(new AxiLiteAxBundle, entries = 1))
  private val arq = Module(new Queue(new AxiLiteAxBundle, entries = 1))
  private val wq = Module(new Queue(new AxiLiteWBundle, entries = 1))
  private val bq = Module(new Queue(new AxiLiteBBundle, entries = 1))
  private val rq = Module(new Queue(new AxiLiteRBundle, entries = 1))

  awq.io.enq <> io.s_axi_cfg.aw
  arq.io.enq <> io.s_axi_cfg.ar
  wq.io.enq <> io.s_axi_cfg.w

  io.s_axi_cfg.b <> bq.io.deq
  io.s_axi_cfg.r <> rq.io.deq

  val cfg = Wire(new ConfigBundle)
  cfg := DontCare
  cfg.we := awq.io.deq.valid && wq.io.deq.valid
  cfg.wa := awq.io.deq.bits.addr
  cfg.wd := wq.io.deq.bits.data
  cfg.wm := VecInit(Seq.tabulate(32)(i => wq.io.deq.bits.strb(i))).asUInt
  cfg.re := arq.io.deq.valid
  cfg.ra := arq.io.deq.bits.addr

  bq.io.enq.valid := cfg.we
  bq.io.enq.bits.resp := 0.U
  rq.io.enq.valid := arq.io.deq.valid
  rq.io.enq.bits.data := cfg.rd
  rq.io.enq.bits.resp := 0.U

  awq.io.enq.ready := wq.io.deq.valid && bq.io.enq.ready
  wq.io.enq.ready := awq.io.deq.valid && bq.io.enq.ready
  arq.io.deq.ready := rq.io.enq.ready
}
