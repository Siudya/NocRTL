package nocrtl.router.compute

import chisel3._
import chisel3.util._
import nocrtl.params.{NocParamsKey, NodeParams, VnParams}
import nocrtl.router.port.LinkBufferToComputeBundle
import nocrtl.utils.NtoMAllocator
import org.chipsalliance.cde.config.Parameters

class NetworkCompute(nodeP:NodeParams, vnStr:String)(implicit p:Parameters) extends Module {
  override val desiredName = s"NetworkCompute${vnStr.capitalize}"
  private val portToVnMap = nodeP.ports.flatMap(port => {
    val vns = port.link.flatMap(_.vns).filter(_.typeStr == vnStr)
    Option.when(vns.nonEmpty)((port.dirStr, vns.head))
  })

  val portToCompPort = portToVnMap.map(elm => (elm._1, IO(Flipped(new LinkBufferToComputeBundle(elm._2))))).toMap
  portToCompPort.foreach({case(a, b) => b.suggestName(s"comp_$a")})

  private val portIdMap = p(NocParamsKey).portIdMap
  private val portToNextRouterMap = (for(port <- nodeP.ports) yield {
    val nextRouter = port.link.head.ports.filter(_.node.get != port.node.get).head.node.get.id.U
    val portId = p(NocParamsKey).portIdMap(port.dirStr)
    (portId, nextRouter)
  }).toMap

  private val routerIdVec = Seq.tabulate(portToNextRouterMap.keys.max)(i => {
    if(portToNextRouterMap.contains(i)) portToNextRouterMap(i)
    else 0.U
  })

  private val vs = Module(p(NocParamsKey).vsMod(portToCompPort.size, nodeP.id).get)

  private val portToCompPortSeq = portToCompPort.toSeq
  private val portToVsQuery = (for(i <- portToCompPortSeq.indices) yield {
    (portToCompPortSeq(i)._1, vs.io.query(i))
  }).toMap

  for((name, comp) <- portToCompPort) {
    val reqs = portToCompPort(name).va
    val rsrc = comp.bs
    val vsq = portToVsQuery(name)
    val va = Module(new NtoMAllocator(reqs.size, rsrc.size))
    vsq.outPort := portIdMap(name).U
    vsq.candidateVc := VecInit(rsrc.map(_.avail)).asUInt
    va.io.req := VecInit(reqs.map(r => r.req && r.curOPort(portIdMap(name)))).asUInt
    va.io.rsrc := vsq.selectedVc
    for(i <- reqs.indices) {
      val rc = Module(p(NocParamsKey).rcMod().get)
      rc.io.router := Mux1H(reqs(i).curOPort, routerIdVec)
      rc.io.dest := reqs(i).dest
      reqs(i).nextOPort := rc.io.portOH
      reqs(i).ack := va.io.grnt(i)
      reqs(i).nextOVCOH := va.io.sel
      rc.suggestName(s"rc_${name}_$i")
    }
    for(i <- rsrc.indices) {
      rsrc(i).take := va.io.sel(i)
    }
  }
}
