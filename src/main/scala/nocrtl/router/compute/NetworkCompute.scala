package nocrtl.router.compute

import chisel3._
import nocrtl.bundle.AxiLiteBundle
import nocrtl.params.{NocParamsKey, NodeParams}
import nocrtl.router.port.LinkBufferToComputeBundle
import nocrtl.utils.NtoMAllocator
import org.chipsalliance.cde.config.Parameters

class NetworkCompute(nodeP:NodeParams, vnStr:String)(implicit p:Parameters) extends Module {
  override val desiredName = s"NetworkComputeR${nodeP.id}${vnStr.toUpperCase}"
  private val portToVnMap = nodeP.ports.flatMap(port => {
    val vns = port.link.flatMap(_.vns).filter(_.typeStr == vnStr)
    Option.when(vns.nonEmpty)((port.dirStr, vns.head))
  })

  val s_axi = IO(Flipped(new AxiLiteBundle))

  val routerCfgMap = portToVnMap.map(elm => (elm._1, IO(Input(UInt(p(NocParamsKey).idBits.W))))).toMap
  routerCfgMap.foreach({case(a, b) => b.suggestName(s"port_${a}_router")})

  val portToCompPort = portToVnMap.map(elm => (elm._1, IO(Flipped(new LinkBufferToComputeBundle(elm._2))))).toMap
  portToCompPort.foreach({case(a, b) => b.suggestName(s"comp_$a")})

  private val portIdMap = p(NocParamsKey).portIdMap

  private val vs = Module(p(NocParamsKey).vsMod.get(portToCompPort.size, nodeP.id, p))
  vs.io.s_axi_cfg <> s_axi

  private val portToCompPortSeq = portToCompPort.toSeq.filterNot(_._1 == "local")
  private val portToVsQuery = (for(i <- portToCompPortSeq.indices) yield {
    (portToCompPortSeq(i)._1, vs.io.query(i))
  }).toMap

  for((name, comp) <- portToCompPort) {
    val reqs = portToCompPort(name).va
    val rsrc = comp.bs
    val vsq = Option.when(name != "local")(portToVsQuery(name))
    val va = Module(new NtoMAllocator(reqs.size, rsrc.size))
    vsq.foreach(_.outPort := portIdMap(name).U)
    vsq.foreach(_.candidateVc := VecInit(rsrc.map(_.avail)).asUInt)
    va.io.req := VecInit(reqs.map(r => r.req && r.curOPort(portIdMap(name)))).asUInt
    va.io.rsrc := vsq.map(_.selectedVc).getOrElse(VecInit(rsrc.map(_.avail)).asUInt)
    for(i <- reqs.indices) {
      val rc = Module(p(NocParamsKey).rcMod.get(p))
      rc.io.router := routerCfgMap(name)
      rc.io.dest := reqs(i).dest
      reqs(i).nextOPort := rc.io.port
      reqs(i).ack := va.io.grnt(i)
      reqs(i).nextOVCOH := va.io.sel
      rc.suggestName(s"rc_${name}_$i")
    }
    for(i <- rsrc.indices) {
      rsrc(i).take := va.io.sel(i)
    }
  }
}
