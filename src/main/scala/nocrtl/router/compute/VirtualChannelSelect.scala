package nocrtl.router.compute

import chisel3._
import nocrtl.bundle.ConfigBundle
import nocrtl.params.NocParamsKey
import org.chipsalliance.cde.config.Parameters

abstract class VirtualChannelSelect(alloc:Int)(implicit p:Parameters) extends Module {
  private val maxVc = p(NocParamsKey).maxVc
  val io = IO(new Bundle {
    val router = Input(UInt(p(NocParamsKey).idBits.W))
    val outPort = Input(UInt(p(NocParamsKey).portIdBits.W))
    val canOutVc = Output(UInt(p(NocParamsKey).maxVc.W))
    val config = new ConfigBundle
  })
}
