package nocrtl.generator

import chisel3.stage.ChiselGeneratorAnnotation
import circt.stage.{ChiselStage, FirtoolOption}
import nocrtl.network.Interconnect

object Generator {
  val firtoolOpts = Seq(
    FirtoolOption("-O=release"),
    FirtoolOption("--disable-annotation-unknown"),
    FirtoolOption("--strip-debug-info"),
    //    FirtoolOption("--lower-memories"),
    FirtoolOption("--disable-all-randomization"),
    FirtoolOption("--add-vivado-ram-address-conflict-synthesis-bug-workaround"),
    FirtoolOption("--lowering-options=noAlwaysComb," +
      " disallowPortDeclSharing, disallowLocalVariables," +
      " emittedLineLength=120, explicitBitcast," +
      " locationInfoStyle=plain, disallowMuxInlining")
  )
}

object InterconnectGenerator extends App {
  val (config, firrtlOpts) = ArgParser(args)
  (new ChiselStage).execute(firrtlOpts, Generator.firtoolOpts ++ Seq(
    ChiselGeneratorAnnotation(() => new Interconnect()(config))
  ))
}