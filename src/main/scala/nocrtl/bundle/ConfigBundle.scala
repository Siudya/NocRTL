package nocrtl.bundle

import chisel3._
import chisel3.experimental.noPrefix
import chisel3.util.Decoupled
import nocrtl.utils.RRArbiterWithReset

class AxiLiteAxBundle extends Bundle {
  val addr = Output(UInt(48.W))
  val prot = Output(UInt(3.W))
}

class AxiLiteWBundle extends Bundle {
  val data = Output(UInt(32.W))
  val strb = Output(UInt(4.W))
}

class AxiLiteBBundle extends Bundle {
  val resp = Output(UInt(2.W))
}

class AxiLiteRBundle extends Bundle {
  val data = Input(UInt(32.W))
  val resp = Input(UInt(2.W))
}

class AxiLiteBundle extends Bundle {
  val aw = Decoupled(new AxiLiteAxBundle)
  val ar = Decoupled(new AxiLiteAxBundle)
  val w = Decoupled(new AxiLiteWBundle)
  val b = Flipped(Decoupled(new AxiLiteBBundle))
  val r = Flipped(Decoupled(new AxiLiteRBundle))
}

class AxiLiteXbar(inSize:Int, outAddrSeq:Seq[(Long, Long)]) extends Module {
  val s_axi = IO(Vec(inSize, Flipped(new AxiLiteBundle)))
  val m_axi = IO(Vec(outAddrSeq.size, new AxiLiteBundle))
  private val outSize = outAddrSeq.size

  private val awRdyMat = Wire(Vec(inSize, Vec(outSize, Bool())))
  awRdyMat.suggestName("aw_rdy_mat")
  s_axi.zip(awRdyMat).foreach({case(a, b) => a.aw.ready := b.asUInt.orR})
  for(o <- outAddrSeq.indices) noPrefix {
    val arb = Module(new RRArbiterWithReset(new AxiLiteAxBundle, inSize))
    arb.suggestName(s"aw_arb_$o")
    m_axi(o).aw <> arb.io.out
    for(i <- s_axi.indices) {
      arb.io.in(i).valid := s_axi(i).aw.valid && (s_axi(i).aw.bits.addr & outAddrSeq(o)._2.U) === outAddrSeq(i)._1.U
      arb.io.in(i).bits := s_axi(i).aw.bits
      awRdyMat(i)(o) := arb.io.in(i).ready
    }
  }

  private val arRdyMat = Wire(Vec(inSize, Vec(outSize, Bool())))
  arRdyMat.suggestName("ar_rdy_mat")
  s_axi.zip(arRdyMat).foreach({case(a, b) => a.ar.ready := b.asUInt.orR})
  for(o <- outAddrSeq.indices) noPrefix {
    val arb = Module(new RRArbiterWithReset(new AxiLiteAxBundle, inSize))
    arb.suggestName(s"ar_arb_$o")
    m_axi(o).ar <> arb.io.out
    for(i <- s_axi.indices) {
      arb.io.in(i).valid := s_axi(i).ar.valid && (s_axi(i).ar.bits.addr & outAddrSeq(o)._2.U) === outAddrSeq(i)._1.U
      arb.io.in(i).bits := s_axi(i).ar.bits
      arRdyMat(i)(o) := arb.io.in(i).ready
    }
  }

  private val wRdyMat = Wire(Vec(inSize, Vec(outSize, Bool())))
  wRdyMat.suggestName("w_rdy_mat")
  s_axi.zip(wRdyMat).foreach({case(a, b) => a.w.ready := b.asUInt.orR})
  for(o <- outAddrSeq.indices) noPrefix {
    val arb = Module(new RRArbiterWithReset(new AxiLiteWBundle, inSize))
    arb.suggestName(s"w_arb_$o")
    m_axi(o).w <> arb.io.out
    for(i <- s_axi.indices) {
      arb.io.in(i).valid := s_axi(i).w.valid
      arb.io.in(i).bits := s_axi(i).w.bits
      wRdyMat(i)(o) := arb.io.in(i).ready
    }
  }

  private val bRdyMat = Wire(Vec(outSize, Vec(inSize, Bool())))
  bRdyMat.suggestName("b_rdy_mat")
  m_axi.zip(bRdyMat).foreach({case(a, b) => a.b.ready := b.asUInt.orR})
  for(i <- s_axi.indices) noPrefix {
    val arb = Module(new RRArbiterWithReset(new AxiLiteBBundle, outSize))
    arb.suggestName(s"b_arb_$i")
    s_axi(i).b <> arb.io.out
    for(o <- outAddrSeq.indices) {
      arb.io.in(i).valid := m_axi(i).b.valid
      arb.io.in(i).bits := m_axi(i).b.bits
      bRdyMat(o)(i) := arb.io.in(i).ready
    }
  }

  private val rRdyMat = Wire(Vec(outSize, Vec(inSize, Bool())))
  rRdyMat.suggestName("r_rdy_mat")
  m_axi.zip(rRdyMat).foreach({case(a, b) => a.r.ready := b.asUInt.orR})
  for(i <- s_axi.indices) noPrefix {
    val arb = Module(new RRArbiterWithReset(new AxiLiteRBundle, outSize))
    arb.suggestName(s"r_arb_$i")
    s_axi(i).r <> arb.io.out
    for(o <- outAddrSeq.indices) {
      arb.io.in(i).valid := m_axi(i).r.valid
      arb.io.in(i).bits := m_axi(i).r.bits
      rRdyMat(o)(i) := arb.io.in(i).ready
    }
  }
}

class ConfigBundle extends Bundle {
  val we = Input(Bool())
  val wa = Input(UInt(48.W))
  val wd = Input(UInt(32.W))
  val wm = Input(UInt(32.W))
  val re = Input(Bool())
  val ra = Input(UInt(48.W))
  val rd = Output(UInt(32.W))
}
