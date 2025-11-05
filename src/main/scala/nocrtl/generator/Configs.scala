package nocrtl.generator

import nocrtl.network.Mesh
import nocrtl.params.{NocParameters, NocParamsKey}
import org.chipsalliance.cde.config.{Config, Parameters}

import scala.annotation.tailrec

class BaseConfig extends Config((site, here, up) => {
  case NocParamsKey => Mesh.nocP
})

object ArgParser {
  def apply(args: Array[String]): (Parameters, Array[String]) = {

    var firrtlOpts = Array[String]()

    @tailrec
    def parse(config: Parameters, args: List[String]): Parameters = {
      args match {
        case Nil => config

        case "--topology" :: confString :: tail =>
          parse(config.alter((site, here, up) => {
            case NocParamsKey => if(confString == "mesh") {
              Mesh.nocP
            }
          }), tail)

        case option :: tail =>
          firrtlOpts :+= option
          parse(config, tail)
      }
    }

    val finalCfg = parse(new BaseConfig, args.toList)
    (finalCfg, firrtlOpts)
  }
}