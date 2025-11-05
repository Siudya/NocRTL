package nocrtl.bundle

import chisel3._
import nocrtl.params.{NocParamsKey, VnParams}
import org.chipsalliance.cde.config.Parameters

trait FlitCommonFields {
  def vcoh: UInt
  def head:Bool
  def tail:Bool
}

class HeadFlit(vnP: VnParams)(implicit p:Parameters) extends Bundle with FlitCommonFields {
  private val nocP = p(NocParamsKey)
  private val dataBits = vnP.bodyDataBits - 12 - nocP.idBits * 2 - nocP.portIdBits
  require(dataBits >= 0)
  val data = UInt(dataBits.W)
  val txn  = UInt(12.W)
  val sorc = UInt(nocP.idBits.W)
  val dest = UInt(nocP.idBits.W)
  val port = UInt(nocP.portIdBits.W)
  val vc1h = Option.when(vnP.vcs.size > 1)(UInt(vnP.vcs.size.W))
  val tail = Bool()
  val head = Bool()
  def vcoh:UInt = vc1h.getOrElse(1.U)
}

class BodyFlit(val vnP: VnParams) extends Bundle with FlitCommonFields {
  private val dataBits = if(vnP.vcs.size > 1) vnP.width - 2 - vnP.vcs.size else vnP.width - 2
  val data = UInt(dataBits.W)
  val vc1h = Option.when(vnP.vcs.size > 1)(UInt(vnP.vcs.size.W))
  val tail = Bool()
  val head = Bool()
  def vcoh:UInt = vc1h.getOrElse(1.U)
}

class Packet(vnP: VnParams)(implicit p:Parameters) extends Bundle {
  private val nocP = p(NocParamsKey)
  val dat = UInt(vnP.packetDataBits.W)
  val txn = UInt(12.W)
  val src = UInt(nocP.idBits.W)
  val dst = UInt(nocP.idBits.W)
}