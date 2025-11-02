package nocrtl.params

import chisel3.util.log2Ceil
import org.chipsalliance.cde.config.{Field, Parameters}

import scala.collection.mutable

case object NocParamsKey extends Field[NocParameters]

object NocParameters {
  private val networkMap = mutable.HashMap[() => Seq[NodeParams], Seq[NodeParams]]()

  def getNodes(gen: () => Seq[NodeParams]):Seq[NodeParams] = {
    if(networkMap.contains(gen)) networkMap.addOne((gen, gen()))
    networkMap(gen)
  }
}

case class NocParameters (
  networkGen: () => Seq[NodeParams] = () => Seq()
) {
  lazy val nodes: Seq[NodeParams] = NocParameters.getNodes(networkGen)
  lazy val size: Int = nodes.size
  lazy val idBits: Int = log2Ceil(size)
}