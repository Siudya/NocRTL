package nocrtl.router.compute

import chisel3._
import nocrtl.bundle.ConfigBundle
import nocrtl.params.NocParamsKey
import org.chipsalliance.cde.config.Parameters

abstract class RouteCompute(implicit p:Parameters) extends Module {
  val io = IO(new Bundle {
    val router = Input(UInt(p(NocParamsKey).idBits.W))
    val dest = Input(UInt(p(NocParamsKey).idBits.W))
    val portOH = Output(UInt(p(NocParamsKey).portIdBits.W))
    val config = new ConfigBundle
  })
}
