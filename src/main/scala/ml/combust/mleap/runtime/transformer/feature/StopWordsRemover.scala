package ml.combust.mleap.runtime.transformer.feature

import ml.combust.mleap.core.feature.StopWordsRemoverModel
import ml.combust.mleap.core.types.NodeShape
import ml.combust.mleap.runtime.frame.{SimpleTransformer, Transformer}
import ml.combust.mleap.runtime.function.UserDefinedFunction

/**
  * Class which will be called by mleap during runtime for stop word remover
  */
case class StopWordsRemover(override val uid: String = Transformer.uniqueName("stopwords_remove"),
                            override val shape: NodeShape,
                            override val model: StopWordsRemoverModel) extends SimpleTransformer
{
    override val exec: UserDefinedFunction = (label: String) => model(label)
}
