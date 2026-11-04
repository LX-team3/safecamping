import axios from 'axios'

// 백엔드 호출 도우미. 응답 형식은 server/CLAUDE.md ({ success, data, message })
// 성공이면 data 만 돌려주고, 실패면 서버 문구를 담은 Error 를 던진다 → 화면은 e.message 만 보여 주면 된다
// 로그인은 세션 쿠키 방식이라 같은 주소(/api → vite proxy → 8080)로 보내야 쿠키가 따라간다
export async function api(url, { method = 'get', body, params } = {}) {
  try {
    const res = await axios({ url, method, data: body, params })
    return res.data.data
  } catch (e) {
    const error = new Error(e.response?.data?.message || '서버와 연결되지 않습니다.')
    error.status = e.response?.status
    throw error
  }
}
