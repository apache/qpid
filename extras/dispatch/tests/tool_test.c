/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

#include "test_case.h"
#include <stdio.h>
#include <string.h>
#include <qpid/dispatch/ctools.h>

typedef struct item_t {
    DEQ_LINKS(struct item_t);
    char letter;
} item_t;

DEQ_DECLARE(item_t, item_list_t);


static char* list_well_formed(item_list_t list, char *key)
{
    item_t *ptr;
    item_t *last  = 0;
    int     size  = DEQ_SIZE(list);
    int     count = 0;
    char    str[32];

    ptr = DEQ_HEAD(list);
    while (ptr) {
        str[count] = ptr->letter;
        count++;
        if (DEQ_PREV(ptr) != last) return "Corrupt previous link";
        last = ptr;
        ptr = DEQ_NEXT(ptr);
    }
    str[count] = '\0';
    if (strcmp(str, key) != 0) return "Invalid key";

    if (count != size) return "Size different from number of items (forward)";

    count = 0;
    last  = 0;
    ptr   = DEQ_TAIL(list);
    while (ptr) {
        count++;
        if (DEQ_NEXT(ptr) != last) return "Corrupt next link";
        last = ptr;
        ptr = DEQ_PREV(ptr);
    }

    if (count != size) return "Size different from number of items (backward)";

    return 0;
}


static char* test_deq_basic(void *context)
{
    item_list_t  list;
    item_t       item[10];
    item_t      *ptr;
    int          idx;
    char        *subtest;

    DEQ_INIT(list);
    if (DEQ_SIZE(list) != 0) return "Expected zero initial size";

    for (idx = 0; idx < 10; idx++) {
        DEQ_ITEM_INIT(&item[idx]);
        item[idx].letter = 'A' + idx;
        DEQ_INSERT_TAIL(list, &item[idx]);
    }
    if (DEQ_SIZE(list) != 10) return "Expected 10 items in list";

    ptr = DEQ_HEAD(list);
    if (!ptr)               return "Expected valid head item";
    if (DEQ_PREV(ptr))      return "Head item has non-null previous link";
    if (ptr->letter != 'A') return "Expected item A at the head";
    if (DEQ_NEXT(ptr) == 0) return "Head item has null next link";
    subtest = list_well_formed(list, "ABCDEFGHIJ");
    if (subtest) return subtest;

    DEQ_REMOVE_HEAD(list);
    if (DEQ_SIZE(list) != 9) return "Expected 9 items in list";
    ptr = DEQ_HEAD(list);
    if (ptr->letter != 'B')  return "Expected item B at the head";
    subtest = list_well_formed(list, "BCDEFGHIJ");
    if (subtest) return subtest;

    DEQ_REMOVE_TAIL(list);
    if (DEQ_SIZE(list) != 8) return "Expected 8 items in list";
    ptr = DEQ_TAIL(list);
    if (ptr->letter != 'I')  return "Expected item I at the tail";
    subtest = list_well_formed(list, "BCDEFGHI");
    if (subtest) return subtest;

    DEQ_REMOVE(list, &item[4]);
    if (DEQ_SIZE(list) != 7) return "Expected 7 items in list";
    subtest = list_well_formed(list, "BCDFGHI");
    if (subtest) return subtest;

    DEQ_REMOVE(list, &item[1]);
    if (DEQ_SIZE(list) != 6) return "Expected 6 items in list";
    subtest = list_well_formed(list, "CDFGHI");
    if (subtest) return subtest;

    DEQ_REMOVE(list, &item[8]);
    if (DEQ_SIZE(list) != 5) return "Expected 5 items in list";
    subtest = list_well_formed(list, "CDFGH");
    if (subtest) return subtest;

    DEQ_INSERT_HEAD(list, &item[8]);
    if (DEQ_SIZE(list) != 6) return "Expected 6 items in list";
    ptr = DEQ_HEAD(list);
    if (ptr->letter != 'I')  return "Expected item I at the head";
    subtest = list_well_formed(list, "ICDFGH");
    if (subtest) return subtest;

    DEQ_INSERT_AFTER(list, &item[4], &item[7]);
    if (DEQ_SIZE(list) != 7) return "Expected 7 items in list";
    ptr = DEQ_TAIL(list);
    if (ptr->letter != 'E')  return "Expected item E at the head";
    subtest = list_well_formed(list, "ICDFGHE");
    if (subtest) return subtest;

    DEQ_INSERT_AFTER(list, &item[1], &item[5]);
    if (DEQ_SIZE(list) != 8) return "Expected 8 items in list";
    subtest = list_well_formed(list, "ICDFBGHE");
    if (subtest) return subtest;

    if (item[0].prev || item[0].next) return "Unlisted item A has non-null pointers";
    if (item[9].prev || item[9].next) return "Unlisted item J has non-null pointers";

    return 0;
}


int tool_tests(void)
{
    int result = 0;

    TEST_CASE(test_deq_basic, 0);

    return result;
}

