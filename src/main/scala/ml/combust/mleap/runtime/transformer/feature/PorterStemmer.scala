package ml.combust.mleap.runtime.transformer.feature

import ml.combust.mleap.core.feature.PorterStemmerModel
import ml.combust.mleap.core.types.NodeShape
import ml.combust.mleap.runtime.frame.{SimpleTransformer, Transformer}
import ml.combust.mleap.runtime.function.UserDefinedFunction

/**
  * Runtime function for porter stemmer.
  */
case class PorterStemmer(override val uid: String = Transformer.uniqueName("porter_stemmer"),
                         override val shape: NodeShape,
                         override val model: PorterStemmerModel) extends SimpleTransformer
{
    override val exec: UserDefinedFunction = (label: String) => model(label)
}