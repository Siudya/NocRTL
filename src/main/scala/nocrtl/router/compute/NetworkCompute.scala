package nocrtl.router.compute

import chisel3._
import chisel3.experimental.noPrefix
import chisel3.util._
import nocrtl.bundle.HeadFlit
import nocrtl.params.{NocParamsKey, NodeParams, VnParams}
import nocrtl.router.port.LinkBufferVnPortBundle
import org.chipsalliance.cde.config.Parameters

class NetworkCompute(nodeP:NodeParams, vnStr:String)(implicit p:Parameters) extends Module {
  override val desiredName = s"NetworkCompute${vnStr.capitalize}"
  private val io = IO(new Bundle {

  })
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

  private val portsMap = (for(port <- nodeP.ports) yield noPrefix {
    val vns = port.link.flatMap(_.vns).filter(_.typeStr == vnStr)
    require(vns.size == 1, s"Multiple VN named $vnStr")
    val vn = vns.head
    val vnPort = IO(Flipped(new LinkBufferVnPortBundle(vn)))
    vnPort.suggestName(s"in_port_${port.dirStr}")
    (port.dirStr, vnPort)
  }).toMap

  private val vs = Module(p(NocParamsKey).vsMod(nodeP.ports.size).get)
  private val recvs = portsMap.flatMap(elm => elm._2.recv.zipWithIndex.map(r => (r._1, (elm._1, r._2)))).toSeq
  private val sends = portsMap.map(elm => (elm._1, elm._2.send.state.zipWithIndex))

  vs.io.router := nodeP.id.U
  for((portStr, sendVcs) <- sends) {
    val recvVec = for(i <- recvs.indices) yield {
      val from = recvs(i)._2._1
      val vnP = nodeP(from).link.flatMap(_.vns).filter(_.typeStr == vnStr).head
      val flitView = recvs(i)._1.bits.asTypeOf(new HeadFlit(vnP))
      val sel = recvs(i)._1.valid && flitView.head && flitView.ptoh(portIdMap(portStr))
      sel
    }
    val sendVec = sendVcs.map(_._1.avail)
  }
}
