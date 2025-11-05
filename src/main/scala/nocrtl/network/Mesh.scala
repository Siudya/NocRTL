package nocrtl.network

import chisel3._
import nocrtl.params._
import nocrtl.router.compute.{RouteCompute, VirtualChannelSelect}
import org.chipsalliance.cde.config.Parameters

object Mesh {
  val portSeq = Seq("x_pos", "x_neg", "y_pos", "y_neg", "local")

  val vnSeq = Seq("data")

  val k = 4

  def buildNetwork():Seq[NodeParams] = {
    val size = k * k

    val nodes = Seq.tabulate(size)(i => {
      val x = i % k
      val y = i / k
      val x_pos = Option.when(x != k - 1)(PortParams("x_pos"))
      val x_neg = Option.when(x != 0)(PortParams("x_neg"))
      val y_pos = Option.when(y != k - 1)(PortParams("y_pos"))
      val y_neg = Option.when(y != 0)(PortParams("y_neg"))
      val local = PortParams("local")
      NodeParams(
        ports = (x_pos ++ x_neg ++ y_pos ++ y_neg).toSeq ++ Seq(local),
        id = i
      )
    })
    val linksNum = k * (k - 1)
    val xLinks = Seq.tabulate(linksNum)(i => {
      LinkParams(vns = Seq(VnParams(vcs = Seq.fill(2)(VcParams()), typeStr = "data")))
    })
    val yLinks = Seq.tabulate(linksNum)(i => {
      LinkParams(vns = Seq(VnParams(vcs = Seq.fill(2)(VcParams()), typeStr = "data")))
    })
    val locLinks = Seq.tabulate(size)(i => {
      LinkParams(vns = Seq(VnParams(vcs = Seq.fill(1)(VcParams()), typeStr = "data")))
    })

    def getNid(x:Int, y:Int):Int = y * k + x
    def getLid(x:Int, y:Int):Int = y * (k - 1) + x
    for(i <- nodes.indices) {
      val x = i % k
      val y = i / k
      val n = nodes(i)
      val right = Option.when(x != k - 1)(nodes(getNid(x + 1, y)))
      val down = Option.when(y != k - 1)(nodes(getNid(x, y + 1)))
      val xlink = Option.when(x != k - 1)(xLinks(getLid(x, y)))
      val ylink = Option.when(y != k - 1)(yLinks(getLid(x, y)))

      if(right.isDefined) n("x_pos") <> (xlink.get <> right.get("x_neg"))
      if(down.isDefined) n("y_pos") <> (ylink.get <> down.get("y_neg"))
      n("local") <> locLinks(i)
    }
    nodes
  }

  def rcGen(p:Parameters) = new MeshRC()(p)

  def vsGen(a:Int, b:Int, p:Parameters) = new MeshVS(a, b)(p)

  val nocP = NocParameters(
    networkGen = buildNetwork,
    vnSeq = vnSeq,
    portSeq = portSeq,
    rcMod = Some(rcGen),
    vsMod = Some(vsGen)
  )
}

class MeshRC(implicit p:Parameters) extends RouteCompute {
  private val curx = io.router % Mesh.k.U
  private val cury = io.router / Mesh.k.U
  private val dstx = io.dest % Mesh.k.U
  private val dsty = io.dest / Mesh.k.U
  private val portIdMap = p(NocParamsKey).portIdMap

  when(curx =/= dstx) {
    io.port := Mux(curx < dstx, portIdMap("x_pos").U, portIdMap("x_neg").U)
  }.elsewhen(cury =/= dsty) {
    io.port := Mux(cury < dsty, portIdMap("y_pos").U, portIdMap("y_neg").U)
  }.otherwise {
    io.port := portIdMap("local").U
  }
}

class MeshVS(querySize:Int, router:Int)(implicit p:Parameters) extends VirtualChannelSelect(querySize, router) {
  for(q <- io.query) {
    q.selectedVc := q.candidateVc
  }
}