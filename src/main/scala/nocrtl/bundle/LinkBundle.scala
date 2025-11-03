package nocrtl.bundle
import chisel3._
import chisel3.util.{MixedVec, Valid}
import nocrtl.params.{LinkParams, VcParams, VnParams}
import org.chipsalliance.cde.config.Parameters

class VnLinkCredit(vnP: VnParams) extends Bundle {
  val token = Input(UInt(vnP.vcs.size.W))
  val tail = Input(UInt(vnP.vcs.size.W))
}

class VnLinkBundle(val vnP: VnParams) extends Bundle {
  val flit = Output(Valid(UInt(vnP.flitBits.W)))
  val crdt = Input(Valid(new VnLinkCredit(vnP)))
}

class LinkCredit(lnkP:LinkParams) extends Bundle {
  val token = Input(MixedVec(lnkP.vns.map(vn => UInt(vn.vcs.size.W))))
  val tail = Input(MixedVec(lnkP.vns.map(vn => UInt(vn.vcs.size.W))))
}

class LinkPayload(lnkP:LinkParams) extends Bundle {
  val data = UInt(lnkP.flitBits.W)
  val vnoh = UInt(lnkP.vns.size.W)
}

class LinkBundle(val lnkP:LinkParams) extends Bundle {
  val flit = Output(Valid(new LinkPayload(lnkP)))
  val crdt = Input(Valid(new LinkCredit(lnkP)))
}