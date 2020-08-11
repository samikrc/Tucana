package ml.combust.mleap.runtime.transformer.feature

import ml.combust.mleap.core.feature.WordSubstitutionModel
import ml.combust.mleap.core.types.NodeShape
import ml.combust.mleap.runtime.frame.{SimpleTransformer, Transformer}
import ml.combust.mleap.runtime.function.UserDefinedFunction

/**
  * Class which will be called by mleap during runtime for word substitution
  */
case class WordSubstitution(override val uid: String = Transformer.uniqueName("word_substitute"),
                            override val shape: NodeShape,
                            override val model: WordSubstitutionModel) extends SimpleTransformer
{
    override val exec: UserDefinedFunction = (label: String) => model(label)
}
