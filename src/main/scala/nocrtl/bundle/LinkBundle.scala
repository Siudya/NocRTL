package nocrtl.bundle
import chisel3._
import nocrtl.params.VnParams
import org.chipsalliance.cde.config.Parameters

class LinkBundle(val vnP: VnParams)(implicit p:Parameters) extends Bundle {
  val vlid = Output(Bool())
  val flit = Output(new FlitBundle(vnP))
  val grnt = Input(Bool())
  val tokn = Option.when(vnP.vcs.size > 1)(Input(UInt(vnP.vcs.size.W)))
  val tail = Option.when(vnP.atomicBuf)(Input(UInt(vnP.vcs.size.W)))
}
