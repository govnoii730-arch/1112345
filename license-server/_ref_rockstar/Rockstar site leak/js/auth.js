// Используем window.API_BASE_URL, чтобы избежать конфликта с другими скриптами
(function() {
    // Всегда используем чистый origin, чтобы избежать дублирования домена
    const origin = window.location.origin;
    const expectedBaseUrl = `${origin}/api/v1`;
    
    // Проверяем, установлен ли API_BASE_URL и не содержит ли он дублирования домена
    if (typeof window.API_BASE_URL === 'undefined' || 
        window.API_BASE_URL === null || 
        window.API_BASE_URL === 'undefined' || 
        !window.API_BASE_URL ||
        window.API_BASE_URL.includes(`${origin}/${origin}`) ||
        window.API_BASE_URL.includes('rockstar.pub/rockstar.pub')) {
        // Устанавливаем правильный базовый URL
        window.API_BASE_URL = expectedBaseUrl;
    }
})();
// Используем window.API_BASE_URL напрямую, чтобы избежать повторного объявления const
var API_BASE_URL = window.API_BASE_URL || (window.location.origin + '/api/v1');

const TOKEN_KEY = 'jwtToken';
let jwtToken = localStorage.getItem(TOKEN_KEY);

const setAuthToken = (token) => {
    if (token) {
        localStorage.setItem(TOKEN_KEY, token);
        jwtToken = token;
    } else {
        localStorage.removeItem(TOKEN_KEY);
        jwtToken = null;
    }
};

const apiRequest = async (endpoint, method = 'GET', body = null, authRequired = true) => {
  const headers = {
    'Content-Type': 'application/json',
  };

  if (authRequired && jwtToken) {
    headers['Authorization'] = `Bearer ${jwtToken}`;
  }

  const res = await fetch(`${API_BASE_URL}${endpoint}`, {
    method,
    headers,
    body: body ? JSON.stringify(body) : null,
    credentials: 'include',
    mode: 'cors',
  });

  let data = null;
  try { 
    const text = await res.text();
    if (text) {
      try {
        data = JSON.parse(text);
      } catch (e) {
        data = text;
      }
    }
  } catch (e) { 
    console.error('Error reading response', e); 
  }

  if (!res.ok) {
    const msg = (data && data.message) ? data.message : `API request failed (${res.status})`;
    const err = new Error(msg);
    err.response = res;
    err.data = data;
    throw err;
  }

  if (data && data.error) {
    const msg = data.error || 'API request failed';
    const err = new Error(msg);
    err.response = res;
    err.data = data;
    throw err;
  }

  return data;
};

const verifyToken = async () => {
    return true;
};

const checkAuth = async () => {
};

document.addEventListener('DOMContentLoaded', () => {
    const protectedPages = ['profile'];
    const currentPage = window.location.pathname.split('/').pop();
    
    if (protectedPages.includes(currentPage)) {
        checkAuth();
    }
});

const getValueByIds = (...ids) => {
  for (const id of ids) {
    const el = document.getElementById(id);
    if (el) return el.value;
  }
  return '';
};

let hCaptchaToken = null;

function onCaptchaVerify(token) {
  hCaptchaToken = token;
}

function onCaptchaExpired() {
  hCaptchaToken = null;
}

async function handleRegistration(event) {
  if (event && event.preventDefault) event.preventDefault();

  const login = getValueByIds('login', 'username', 'user');
  const email = getValueByIds('email', 'mail');
  const password = getValueByIds('pass', 'password', 'pwd');

  const termsEl = document.getElementById('terms');
  if (termsEl && !termsEl.checked) {
    alert('Подтвердите соглашение с правилами.');
    return;
  }

  try {
    // Убеждаемся, что API_BASE_URL определен
    if (!API_BASE_URL || API_BASE_URL === 'undefined' || API_BASE_URL === undefined) {
      API_BASE_URL = window.API_BASE_URL || 'http://localhost:8081/api/v1';
      window.API_BASE_URL = API_BASE_URL;
    }

    const payload = { login, email, password };
    const signupUrl = `${API_BASE_URL}/auth/signup?token=${encodeURIComponent(hCaptchaToken)}`;
    console.log('Signup URL:', signupUrl); // Для отладки
    
    const response = await fetch(signupUrl, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      credentials: 'include',
      mode: 'cors',
      body: JSON.stringify(payload)
    });

    if (!response.ok) {
      let errorText = '';
      try {
        errorText = await response.text();
      } catch (e) {
        errorText = `HTTP error! status: ${response.status}`;
      }
      throw new Error(errorText || `HTTP error! status: ${response.status}`);
    }

    let responseData;
    const contentType = response.headers.get('content-type');
    if (contentType && contentType.includes('application/json')) {
      responseData = await response.json();
    } else {
      const text = await response.text();
      try {
        responseData = JSON.parse(text);
      } catch (e) {
        throw new Error(text || 'Ошибка при регистрации');
      }
    }

    const token = (responseData && (responseData.token || (responseData.data && responseData.data.token))) || null;
    
    if (token) {
      setAuthToken(token);
      window.location.href = '/profile';
    } else {
      throw new Error('Токен не получен');
    }
  } catch (error) {
    console.error('Registration error:', error);
    hCaptchaToken = null;
    if (window.hcaptcha) {
      window.hcaptcha.reset();
    }
    const msg = error.message || 'Ошибка при регистрации';
    alert(msg);
  }
}

window.onCaptchaVerify = onCaptchaVerify;
window.onCaptchaExpired = onCaptchaExpired;

const handleSignIn = async (event) => {
    event.preventDefault();
    const signinError = document.getElementById('signinError');
    if (signinError) signinError.textContent = '';

    const login = document.getElementById('login').value;
    const password = document.getElementById('pass').value;

    try {
        const response = await fetch(`${API_BASE_URL}/authorization`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
            },
            credentials: 'include',
            mode: 'cors',
            body: new URLSearchParams({
                statement: login,
                password: password
            })
        });

        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }

        const responseData = await response.json();

        // Проверяем, что это именно запрос на 2FA, а не ошибка пароля
        if (responseData && responseData.reason && responseData.reason === "Введите код из Google Authenticator") {
            show2FAField(login);
            if (signinError) {
                signinError.textContent = responseData.reason;
                signinError.style.color = '#4CAF50';
            }
            return;
        }

        // Если есть reason, но это не 2FA - показываем ошибку
        if (responseData && responseData.reason) {
            if (signinError) {
                signinError.textContent = responseData.reason;
                signinError.style.color = '#f44336';
            }
            return;
        }

        if (responseData && responseData.success) {
            const token = responseData.token || responseData.data?.token;
            if (token) {
                setAuthToken(token);
            }
            window.location.href = '/profile';
        } else if (responseData && responseData.error) {
            throw new Error(responseData.error);
        } else {
            throw new Error('Ошибка при авторизации');
        }
    } catch (error) {
        console.error('Ошибка входа:', error);
        const errorMessage = error.message || 'Ошибка при входе';
        
        if (signinError) {
            signinError.textContent = errorMessage;
            signinError.style.color = '#f44336';
        } else {
            alert(errorMessage);
        }
    }
};

let currentLoginFor2FA = '';

const show2FAField = (login) => {
    currentLoginFor2FA = login;
    
    const form = document.getElementById('signinForm');
    if (form) {
        form.style.display = 'none';
    }

    let twoFAContainer = document.getElementById('twoFAContainer');
    if (!twoFAContainer) {
        twoFAContainer = document.createElement('div');
        twoFAContainer.id = 'twoFAContainer';
        twoFAContainer.className = 'twofa-container';
        
        const formContainer = document.querySelector('.form-container');
        if (formContainer) {
            formContainer.appendChild(twoFAContainer);
        }
    }

    twoFAContainer.innerHTML = `
        <div class="text">
            <h2>Подтверждение 2FA</h2>
            <p>Введите код из Google Authenticator</p>
        </div>
        <form class="form" id="twoFAForm" onsubmit="handle2FAVerification(event)">
            <div class="input-group">
                <label for="twoFACode">Код из Google Authenticator</label>
                <div class="input-wrapper">
                    <input type="text" id="twoFACode" name="twoFACode" placeholder="Введите 6-значный код" maxlength="6" required autocomplete="off">
                </div>
            </div>
            <div id="twoFAError" class="error-message"></div>
            <button type="submit" class="submit-btn">Подтвердить</button>
            <div class="account" style="margin-top: 15px;">
                <button type="button" onclick="cancel2FA()" class="cancel-btn" style="background: transparent; border: none; color: #999; cursor: pointer; text-decoration: underline;">Отмена</button>
            </div>
        </form>
    `;

    setTimeout(() => {
        const codeInput = document.getElementById('twoFACode');
        if (codeInput) {
            codeInput.focus();
        }
    }, 100);
};

const cancel2FA = () => {
    const twoFAContainer = document.getElementById('twoFAContainer');
    if (twoFAContainer) {
        twoFAContainer.remove();
    }
    
    const form = document.getElementById('signinForm');
    if (form) {
        form.style.display = 'block';
    }
    
    currentLoginFor2FA = '';
    
    const signinError = document.getElementById('signinError');
    if (signinError) {
        signinError.textContent = '';
    }
};

const handle2FAVerification = async (event) => {
    event.preventDefault();
    const twoFAError = document.getElementById('twoFAError');
    if (twoFAError) twoFAError.textContent = '';

    const code = document.getElementById('twoFACode').value;

    if (!code || code.length !== 6) {
        if (twoFAError) {
            twoFAError.textContent = 'Введите 6-значный код';
            twoFAError.style.color = '#f44336';
        }
        return;
    }

    try {
        const response = await fetch(`${API_BASE_URL}/verify-2fa`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
            },
            credentials: 'include',
            mode: 'cors',
            body: new URLSearchParams({
                statement: currentLoginFor2FA,
                code: code
            })
        });

        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }

        const data = await response.json();

        if (data && data.success) {
            const token = data.token || data.data?.token;
            if (token) {
                setAuthToken(token);
            }
            window.location.href = '/profile';
        } else if (data && data.error) {
            if (twoFAError) {
                twoFAError.textContent = data.error;
                twoFAError.style.color = '#f44336';
            }
        } else {
            throw new Error('Ошибка при верификации');
        }
    } catch (error) {
        console.error('Ошибка верификации 2FA:', error);
        const errorMessage = error.data?.error || 'Ошибка при верификации 2FA';
        if (twoFAError) {
            twoFAError.textContent = errorMessage;
            twoFAError.style.color = '#f44336';
        }
    }
};

window.handle2FAVerification = handle2FAVerification;
window.cancel2FA = cancel2FA;

document.addEventListener('DOMContentLoaded', () => {
    const signinForm = document.getElementById('signinForm');
    if (signinForm) {
        signinForm.addEventListener('submit', handleSignIn);
    }
});

window.handleRegistration = handleRegistration;
window.verifyToken = verifyToken;
window.setAuthToken = setAuthToken;