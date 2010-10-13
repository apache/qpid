package org.apache.qpid.server.exchange.topic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.test.utils.QpidTestCase;

/**
 * This <em>should</em> test the {@link TopicParser}.
 * 
 * TODO add test case assertions
 */
public class TopicParserTest extends QpidTestCase
{
    public void _estParser()
    {
        printMatches("#.b.*.*.*.*.*.h.#.j.*.*.*.*.*.*.q.#.r.*.*.*.*.*.*.*.*","a.b.c.d.e.f.g.h.i.j.k.l.m.n.o.p.q.r.s.t.u.v.w.x.y.z");
        printMatches(new String[]{
                        "#.a.#",
                        "#.b.#",
                        "#.c.#",
                        "#.d.#",
                        "#.e.#",
                        "#.f.#",
                        "#.g.#",
                        "#.h.#",
                        "#.i.#",
                        "#.j.#",
                        "#.k.#",
                        "#.l.#",
                        "#.m.#",
                        "#.n.#",
                        "#.o.#",
                        "#.p.#",
                        "#.q.#"

        }, "a.b.c.d.e.f.g.h.i.j.k.l.m.n.o.p.q.r.s.t.u.v.w.x.y.z");        
/*
        printMatches(new String[]{
                "#.a.#",
                "#.b.#",
                "#.c.#",
                "#.d.#",
                "#.e.#",
                "#.f.#",
                "#.g.#",
                "#.h.#",
                "#.i.#",
                "#.j.#",
                "#.k.#",
                "#.l.#",
                "#.m.#",
                "#.n.#",
                "#.o.#",
                "#.p.#",
                "#.q.#",
                "#.r.#",
                "#.s.#",
                "#.t.#",
                "#.u.#",
                "#.v.#",
                "#.w.#",
                "#.x.#",
                "#.y.#",
                "#.z.#"


        },"a.b");

        printMatches("#.b.*.*.*.*.*.h.#.j.*.*.*.*.*.p.#.r.*.*.*.*.*","a.b.c.d.e.f.g.h.i.j.k.l.m.n.o.p.q.r.s.t.u.v.w.x.y.z");
        printMatches("#.b.*.*.*.*.*.h.#.j.*.*.*.*.*.p.#.r.*.*.*.*.*.*.*.*","a.b.c.d.e.f.g.h.i.j.k.l.m.n.o.p.q.r.s.t.u.v.w.x.y.z");
        printMatches("a.#.b.#","a.b.b.b.b.b.b.b.c");

*/

        printMatches("","");
        printMatches("a","a");
        printMatches("a","");
        printMatches("","a");
        printMatches("a.b","a.b");
        printMatches("a","a.b");
        printMatches("a.b","a");
        printMatches("*","a");
        printMatches("*.b","a.b");
        printMatches("*.*","a.b");
        printMatches("a.*","a.b");
        printMatches("a.*.#","a.b");
        printMatches("a.#.b","a.b");

        printMatches("#.b","a");
        printMatches("#.b","a.b");
        printMatches("#.a.b","a.b");

        printMatches("#","");
        printMatches("#","a");
        printMatches("#","a.b");
        printMatches("#.#","a.b");
        printMatches("#.*","a.b");

        printMatches("#.a.b","a.b");
        printMatches("a.b.#","a.b");
        printMatches("a.#","a.b");
        printMatches("#.*.#","a.b");
        printMatches("#.*.b.#","a.b");
        printMatches("#.a.*.#","a.b");
        printMatches("#.a.#.b.#","a.b");
        printMatches("#.*.#.*.#","a.b");
        printMatches("*.#.*.#","a.b");
        printMatches("#.*.#.*","a.b");

        printMatches(new String[]{"a.#.b.#","a.*.#.b.#"},"a.b.b.b.b.b.b.b.c");

        printMatches(new String[]{"a.b", "a.c"},"a.b");
        printMatches(new String[]{"a.#", "a.c", "#.b"},"a.b");
        printMatches(new String[]{"a.#", "a.c", "#.b", "#", "*.*"},"a.b");

        printMatches(new String[]{"a.b.c.d.e.#", "a.b.c.d.#", "a.b.c.d.*", "a.b.c.#", "#.e", "a.*.c.d.e","#.c.*.#.*.*"},"a.b.c.d.e");
        printMatches(new String[]{"a.b.c.d.e.#", "a.b.c.d.#", "a.b.c.d.*", "a.b.c.#", "#.e", "a.*.c.d.e","#.c.*.#.*.*"},"a.b.c.d.f.g");
    }

    private void printMatches(final String[] bindingKeys, final String routingKey)
    {
        TopicMatcherDFAState sm = null;
        Map<TopicMatcherResult, String> resultMap = new HashMap<TopicMatcherResult, String>();

        TopicParser parser = new TopicParser();

        long start = System.currentTimeMillis();
        for(int i = 0; i < bindingKeys.length; i++)
        {
            _logger.debug((System.currentTimeMillis() - start) + ":\t" + bindingKeys[i]);
            TopicMatcherResult r = new TopicMatcherResult(){};
            resultMap.put(r, bindingKeys[i]);
            AMQShortString bindingKeyShortString = new AMQShortString(bindingKeys[i]);

            _logger.info("=====================================================");
            _logger.info("Adding binding key: " + bindingKeyShortString);
            _logger.info("-----------------------------------------------------");


            if(i==0)
            {
                sm = parser.createStateMachine(bindingKeyShortString, r);
            }
            else
            {
                sm = sm.mergeStateMachines(parser.createStateMachine(bindingKeyShortString, r));
            }
            _logger.debug(sm.reachableStates());
            _logger.debug("=====================================================");
        }
        AMQShortString routingKeyShortString = new AMQShortString(routingKey);

        Collection<TopicMatcherResult> results = sm.parse(parser.getDictionary(), routingKeyShortString);
        Collection<String> resultStrings = new ArrayList<String>();

        for(TopicMatcherResult result : results)
        {
            resultStrings.add(resultMap.get(result));
        }

        final ArrayList<String> nonMatches = new ArrayList<String>(Arrays.asList(bindingKeys));
        nonMatches.removeAll(resultStrings);
        _logger.info("\""+routingKeyShortString+"\" matched with " + resultStrings + " DID NOT MATCH with " + nonMatches);
    }

    private void printMatches(String bindingKey, String routingKey)
    {
        printMatches(new String[] { bindingKey }, routingKey);
    }
}
