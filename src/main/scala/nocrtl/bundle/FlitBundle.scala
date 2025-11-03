package nocrtl.bundle

import chisel3.{Bundle, _}
import nocrtl.params.{NocParamsKey, VnParams}
import org.chipsalliance.cde.config.Parameters

trait FlitCommonFields {
  def vcoh: UInt
  def head:Bool
  def tail:Bool
}

class HeadFlit(val vnP: VnParams)(implicit p:Parameters) extends Bundle with FlitCommonFields {
  private val nocP = p(NocParamsKey)
  val sorc = UInt(nocP.idBits.W)
  val dest = UInt(nocP.idBits.W)
  val ptoh = UInt(nocP.portIdBits.W)
  val vcoh = UInt(vnP.vcs.size.W)
  val tail = Bool()
  val head = Bool()
}

class BodyFlit(val vnP: VnParams) extends Bundle with FlitCommonFields {
  val data = UInt(vnP.width.W)
  val vcoh = UInt(vnP.vcs.size.W)
  val tail = Bool()
  val head = Bool()
}