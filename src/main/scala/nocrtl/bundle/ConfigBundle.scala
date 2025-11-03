package nocrtl.bundle

import chisel3._

class ConfigBundle extends Bundle {
  val we = Input(Bool())
  val wd = Input(UInt(32.W))
  val wm = Input(UInt(4.W))
  val re = Input(Bool())
  val rd = Output(UInt(32.W))
}
