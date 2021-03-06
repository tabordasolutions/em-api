/**
 * Copyright (c) 2008-2018, Massachusetts Institute of Technology (MIT)
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package edu.mit.ll.em.api.rs.exceptionMapper;

import edu.mit.ll.em.api.rs.RegisterUser;
import edu.mit.ll.em.api.rs.ValidationErrorResponse;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import javax.validation.*;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;

public class ValidationExceptionMapperTest {

    private Validator validator;
    private ValidationExceptionMapper errorResponseMapper = new ValidationExceptionMapper();

    @Before
    public void setup() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    public void verifyValidationErrorsAreAddedInResponseBody() throws IOException {
        RegisterUser user = new RegisterUser(1, 8, "first", "last", "hehe", "phone", "password", new ArrayList<Integer>());
        Set<ConstraintViolation<RegisterUser>> violations = this.getConstraintViolations(user);
        ConstraintViolationException exception = new ConstraintViolationException(violations);

        Response response = errorResponseMapper.toResponse(exception);
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        ValidationErrorResponse errorResponseEntity = (ValidationErrorResponse) response.getEntity();
        assertEquals(ValidationExceptionMapper.VALIDATION_ERROR_MESSAGE, errorResponseEntity.getMessage());
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), errorResponseEntity.getStatus());
        assertEquals(violations.size(), errorResponseEntity.getValidationErrors().size());
        assertNotNull(errorResponseEntity.getValidationErrors().get("email"));
        assertNotNull(errorResponseEntity.getValidationErrors().get("phone"));
        assertNotNull(errorResponseEntity.getValidationErrors().get("password"));
    }

    private Set<ConstraintViolation<RegisterUser>> getConstraintViolations(RegisterUser user) {
        return validator.validate(user);
    }
}
