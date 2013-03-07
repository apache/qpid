#ifndef __dispatch_user_fd_h__
#define __dispatch_user_fd_h__ 1
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

#include <qpid/dispatch/server.h>

/**
 * \defgroup UserFd Server User-File-Descriptor Functions
 * @{
 */

typedef struct dx_user_fd_t dx_user_fd_t;


/**
 * User_fd Handler
 *
 * Callback invoked when a user-managed file descriptor is available for reading or writing or there
 * was an error on the file descriptor.
 *
 * @param context The handler context supplied in the dx_user_fd call.
 * @param ufd The user_fd handle for the processable fd.
 */
typedef void (*dx_user_fd_handler_cb_t)(void* context, dx_user_fd_t *ufd);


/**
 * Set the user-fd handler callback for the server.  This handler is optional, but must be supplied
 * if the dx_server is used to manage the activation of user file descriptors.
 */
void dx_server_set_user_fd_handler(dx_dispatch_t *dx, dx_user_fd_handler_cb_t ufd_handler);


/**
 * Create a tracker for a user-managed file descriptor.
 *
 * A user-fd is appropriate for use when the application opens and manages file descriptors
 * for purposes other than AMQP communication.  Registering a user fd with the dispatch server
 * controls processing of the FD alongside the FDs used for messaging.
 *
 * @param fd The open file descriptor being managed by the application.
 * @param context User context passed back in the connection handler.
 * @return A pointer to the new user_fd.
 */
dx_user_fd_t *dx_user_fd(dx_dispatch_t *dx, int fd, void *context);


/**
 * Free the resources for a user-managed FD tracker.
 *
 * @param ufd Structure pointer returned by dx_user_fd.
 */
void dx_user_fd_free(dx_user_fd_t *ufd);


/**
 * Activate a user-fd for read.
 *
 * Use this activation when the application has capacity to receive data from the user-fd.  This will
 * cause the callback set in dx_server_set_user_fd_handler to later be invoked when the
 * file descriptor has data to read.
 *
 * @param ufd Structure pointer returned by dx_user_fd.
 */
void dx_user_fd_activate_read(dx_user_fd_t *ufd);


/**
 * Activate a user-fd for write.
 *
 * Use this activation when the application has data to write via the user-fd.  This will
 * cause the callback set in dx_server_set_user_fd_handler to later be invoked when the
 * file descriptor is writable.
 *
 * @param ufd Structure pointer returned by dx_user_fd.
 */
void dx_user_fd_activate_write(dx_user_fd_t *ufd);


/**
 * Check readable status of a user-fd
 *
 * Note: It is possible that readable status is spurious (i.e. this function returns true
 *       but the file-descriptor is not readable and will block if not set to O_NONBLOCK).
 *       Code accordingly.
 *
 * @param ufd Structure pointer returned by dx_user_fd.
 * @return true iff the user file descriptor is readable.
 */
bool dx_user_fd_is_readable(dx_user_fd_t *ufd);


/**
 * Check writable status of a user-fd
 *
 * @param ufd Structure pointer returned by dx_user_fd.
 * @return true iff the user file descriptor is writable.
 */
bool dx_user_fd_is_writeable(dx_user_fd_t *ufd);

/**
 * @}
 */

#endif
