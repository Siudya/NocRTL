package nocrtl.router.crossbar

import chisel3._
import chisel3.util._
import nocrtl.bundle.BodyFlit
import nocrtl.params.{NocParamsKey, NodeParams}
import nocrtl.router.buffer.BufferOutputBundle
import nocrtl.utils.RRArbiterWithReset
import org.chipsalliance.cde.config.Parameters

class Crossbar(nodeP:NodeParams, vnStr:String)(implicit p:Parameters) extends Module {
  override val desiredName = s"CrossBarR${nodeP.id}${vnStr.toUpperCase}"
  private val portToVnMap = nodeP.ports.flatMap(port => {
    val vns = port.link.flatMap(_.vns).filter(_.typeStr == vnStr)
    Option.when(vns.nonEmpty)((port.dirStr, vns.head))
  })
  val portToInwardMap = portToVnMap.map(elm => (elm._1, IO(Vec(elm._2.vcs.size, Flipped(Decoupled(new BufferOutputBundle(elm._2)))))))
  portToInwardMap.foreach({case(a, b) => b.suggestName(s"inward_${a.toLowerCase}")})

  val portToOutwardMap = portToVnMap.map(elm => (elm._1, IO(Vec(elm._2.vcs.size, Decoupled(new BodyFlit(elm._2))))))
  portToOutwardMap.foreach({case(a, b) => b.suggestName(s"outward_${a.toLowerCase}")})

  private val portIdMap = p(NocParamsKey).portIdMap
  private val allInwards = portToInwardMap.flatMap(elm => elm._2.indices.map(i => (elm._1, i, elm._2(i))))
  private val allOutwards = portToOutwardMap.flatMap(elm => elm._2.indices.map(i => (elm._1, i, elm._2(i))))
  private val rdyMat = Wire(Vec(allInwards.size, Vec(allOutwards.size, Bool())))
  dontTouch(rdyMat)
  rdyMat.suggestName(s"rdy_mat")
  for(((opname, ovcid, out), o) <- allOutwards.zipWithIndex) {
    val arb = Module(new RRArbiterWithReset(new BodyFlit(out.bits.vnP), allInwards.size))
    out <> arb.io.out
    for(((_, _, in), i) <- allInwards.zipWithIndex) {
      arb.io.in(i).valid := in.valid && in.bits.port === portIdMap(opname).U && in.bits.vcoh(ovcid)
      arb.io.in(i).bits := in.bits.flit
      rdyMat(i)(o) := arb.io.in(i).ready
    }
  }
  for(i <- allInwards.indices) {
    allInwards(i)._3.ready := rdyMat(i).asUInt.orR
  }
}
