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

#include <qpid/dispatch/bitmask.h>
#include <qpid/dispatch/alloc.h>
#include <assert.h>

#define DX_BITMASK_LONGS 16
#define DX_BITMASK_BITS  (DX_BITMASK_LONGS * 64)

struct dx_bitmask_t {
    uint64_t array[DX_BITMASK_LONGS];
    int      first_set;
};

ALLOC_DECLARE(dx_bitmask_t);
ALLOC_DEFINE(dx_bitmask_t);

#define MASK_INDEX(num)  (num / 64)
#define MASK_ONEHOT(num) (((uint64_t) 1) << (num % 64))
#define FIRST_NONE    -1
#define FIRST_UNKNOWN -2


int dx_bitmask_width()
{
    return DX_BITMASK_BITS;
}


dx_bitmask_t *dx_bitmask(int initial)
{
    dx_bitmask_t *b = new_dx_bitmask_t();
    if (initial)
        dx_bitmask_set_all(b);
    else
        dx_bitmask_clear_all(b);
    return b;
}


void dx_bitmask_free(dx_bitmask_t *b)
{
    free_dx_bitmask_t(b);
}


void dx_bitmask_set_all(dx_bitmask_t *b)
{
    for (int i = 0; i < DX_BITMASK_LONGS; i++)
        b->array[i] = 0xFFFFFFFFFFFFFFFF;
    b->first_set = 0;
}


void dx_bitmask_clear_all(dx_bitmask_t *b)
{
    for (int i = 0; i < DX_BITMASK_LONGS; i++)
        b->array[i] = 0;
    b->first_set = FIRST_NONE;
}


void dx_bitmask_set_bit(dx_bitmask_t *b, int bitnum)
{
    assert(bitnum < DX_BITMASK_BITS);
    b->array[MASK_INDEX(bitnum)] |= MASK_ONEHOT(bitnum);
    if (b->first_set > bitnum || b->first_set < 0)
        b->first_set = bitnum;
}


void dx_bitmask_clear_bit(dx_bitmask_t *b, int bitnum)
{
    assert(bitnum < DX_BITMASK_BITS);
    b->array[MASK_INDEX(bitnum)] &= ~(MASK_ONEHOT(bitnum));
    if (b->first_set == bitnum)
        b->first_set = FIRST_UNKNOWN;
}


int dx_bitmask_value(dx_bitmask_t *b, int bitnum)
{
    return (b->array[MASK_INDEX(bitnum)] & MASK_ONEHOT(bitnum)) ? 1 : 0;
}


int dx_bitmask_first_set(dx_bitmask_t *b, int *bitnum)
{
    if (b->first_set == FIRST_UNKNOWN) {
        b->first_set = FIRST_NONE;
        for (int i = 0; i < DX_BITMASK_LONGS; i++)
            if (b->array[i]) {
                for (int j = 0; j < 64; j++)
                    if ((((uint64_t) 1) << j) & b->array[i]) {
                        b->first_set = i * 64 + j;
                        break;
                    }
                break;
            }
    }

    if (b->first_set == FIRST_NONE)
        return 0;
    *bitnum = b->first_set;
    return 1;
}

