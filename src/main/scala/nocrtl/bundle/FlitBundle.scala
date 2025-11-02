package nocrtl.bundle

import chisel3._
import nocrtl.params.{NocParamsKey, VnParams}
import org.chipsalliance.cde.config.Parameters

class FlitBundle(val vnP: VnParams)(implicit p:Parameters) extends Bundle {
  private val nocP = p(NocParamsKey)
  val sorc  = UInt(nocP.idBits.W)
  val dest = UInt(nocP.idBits.W)
  val vc   = UInt(vnP.vcIdBits.W)
  val head = Bool()
  val tail = Bool()
  val data = UInt(vnP.width.W)
}