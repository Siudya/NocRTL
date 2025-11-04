package nocrtl.router.compute

import chisel3._
import nocrtl.bundle.ConfigBundle
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
    val config = new ConfigBundle
  })
}
