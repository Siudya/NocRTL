package nocrtl.utils

import chisel3._
import chisel3.util.{Cat, PriorityEncoderOH, Valid}

class Allocator(size:Int) extends Module {
  val io = IO(new Bundle {
    val in = Input(UInt(size.W))
    val fire = Input(Bool())
    val sel = Output(Valid(UInt(size.W)))
  })
}

class ISLIPAllocator(size:Int) extends Allocator(size) {
  io.sel.valid := io.in.orR
  if(size == 1) {
    io.sel.bits := io.in
  } else {
    val pmask = RegInit(0.U(size.W))
    val nmask = (~pmask).asUInt
    val fin = Cat(io.in, io.in & nmask)
    val foh = PriorityEncoderOH(fin)
    io.sel.bits := foh(size - 1, 0) | foh(2 * size - 1, size)
    when(io.fire) {
      pmask := (io.sel.bits - 1.U) | io.sel.bits
    }
  }
}

class NtoMAllocator(N:Int, M:Int) extends Module {
  val io = IO(new Bundle {
    val req = Input(UInt(N.W))
    val rsrc = Input(UInt(M.W))
    val grnt = Output(UInt(N.W))
    val sel = Output(UInt(N.W))
  })
  private val reqAlloc = Module(new ISLIPAllocator(N))
  private val rsrcAlloc = Module(new ISLIPAllocator(M))
  private val fire = reqAlloc.io.sel.valid && rsrcAlloc.io.sel.valid

  reqAlloc.io.in := io.req
  reqAlloc.io.fire := fire
  io.grnt := Mux(fire, reqAlloc.io.sel.bits, 0.U)

  rsrcAlloc.io.in := io.rsrc
  rsrcAlloc.io.fire := fire
  io.sel := Mux(fire, rsrcAlloc.io.sel.bits, 0.U)
}