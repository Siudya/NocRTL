package nocrtl.router

import chisel3._
import chisel3.util._
import nocrtl.bundle._
import nocrtl.params.{NocParamsKey, NodeParams}
import nocrtl.router.buffer.BufferOutputBundle
import nocrtl.router.compute.NetworkCompute
import nocrtl.router.crossbar.Crossbar
import nocrtl.router.port.LinkBufferToComputeBundle
import org.chipsalliance.cde.config.Parameters

class VNFabric(nodeP:NodeParams, vnStr:String)(implicit p:Parameters) extends Module {
  override val desiredName = s"VNFabricR${nodeP.id}${vnStr.toUpperCase}"
  private val portToVnMap = nodeP.ports.flatMap(port => {
    val vns = port.link.flatMap(_.vns).filter(_.typeStr == vnStr)
    Option.when(vns.nonEmpty)((port.dirStr, vns.head))
  })

  val s_axi = IO(Flipped(new AxiLiteBundle))

  val routerCfgMap = portToVnMap.map(elm => (elm._1, IO(Input(UInt(p(NocParamsKey).idBits.W))))).toMap
  routerCfgMap.foreach({case(a, b) => b.suggestName(s"port_${a}_router")})

  val portToCompPort = portToVnMap.map(elm => (elm._1, IO(Flipped(new LinkBufferToComputeBundle(elm._2))))).toMap
  portToCompPort.foreach({case(a, b) => b.suggestName(s"comp_$a")})

  val portToInwardMap = portToVnMap.map(elm => (elm._1, IO(Vec(elm._2.vcs.size, Flipped(Decoupled(new BufferOutputBundle(elm._2))))))).toMap
  portToInwardMap.foreach({case(a, b) => b.suggestName(s"inward_${a.toLowerCase}")})

  val portToOutwardMap = portToVnMap.map(elm => (elm._1, IO(Vec(elm._2.vcs.size, Decoupled(new BodyFlit(elm._2)))))).toMap
  portToOutwardMap.foreach({case(a, b) => b.suggestName(s"outward_${a.toLowerCase}")})

  private val comp = Module(new NetworkCompute(nodeP, vnStr))
  private val xbar = Module(new Crossbar(nodeP, vnStr))

  private val xbarInwardPortMap = xbar.portToInwardMap.toMap
  private val xbarOutwardPortMap = xbar.portToOutwardMap.toMap
  for((pname, _) <- portToVnMap) {
    comp.portToCompPort(pname) <> portToCompPort(pname)
    comp.routerCfgMap(pname) := routerCfgMap(pname)
    xbarInwardPortMap(pname) <> portToInwardMap(pname)
    xbarOutwardPortMap(pname) <> portToOutwardMap(pname)
  }
}
