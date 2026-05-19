/*
 * This software is in the public domain under CC0 1.0 Universal plus a
 * Grant of Patent License.
 *
 * To the extent possible under law, the author(s) have dedicated all
 * copyright and related and neighboring rights to this software to the
 * public domain worldwide. This software is distributed without any
 * warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication
 * along with this software (see the LICENSE.md file). If not, see
 * <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

import org.moqui.Moqui
import org.moqui.ai.AiToolFactory
import org.moqui.context.ExecutionContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.*

class AiToolFactoryTests extends Specification {
    static final Logger logger = LoggerFactory.getLogger(AiToolFactoryTests.class)
    @Shared ExecutionContext ec

    def setupSpec() {
        ec = Moqui.getExecutionContext()
    }

    def cleanupSpec() {
        ec.destroy()
    }

    def "generate returns non-empty String"() {
        given:
        def messages = [[role: "user", content: "Say hello in one word."]]

        when:
        def ai = ec.getTool("AI", AiToolFactory.class)
        String response = ai.generate(messages)
        logger.info("generate response: ${response}")

        then:
        response != null
        !response.isEmpty()
    }

    def "generateStructured returns Map with expected keys"() {
        given:
        def messages = [[role: "user", content: "Return a greeting with a single word."]]
        def schema = [word: [type: "string"]]

        when:
        def ai = ec.getTool("AI", AiToolFactory.class)
        Map result = ai.generateStructured(messages, schema)
        logger.info("generateStructured result: ${result}")

        then:
        result != null
        result instanceof Map
        result.containsKey("word")
        result.word != null
        !((String) result.word).isEmpty()
    }
}
